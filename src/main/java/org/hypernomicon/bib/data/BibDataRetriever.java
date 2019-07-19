/*
 * Copyright 2015-2019 Jason Winning
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.hypernomicon.bib.data;

import static org.hypernomicon.bib.data.BibField.BibFieldEnum.*;
import static org.hypernomicon.model.records.SimpleRecordTypes.WorkTypeEnum.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.hypernomicon.model.Exceptions.TerminateTaskException;
import org.hypernomicon.model.records.SimpleRecordTypes.WorkTypeEnum;
import org.hypernomicon.model.relations.ObjectGroup;
import org.hypernomicon.util.AsyncHttpClient;
import org.hypernomicon.util.filePath.FilePath;
import org.json.simple.parser.ParseException;

public class BibDataRetriever
{
  private BibData pdfBD = null, queryBD = null;
  private boolean stopped = false, searchedCrossref = false;

  private final BibData workBD;
  private final AsyncHttpClient httpClient;
  private final WorkTypeEnum workTypeEnum;
  private final List<ObjectGroup> authorGroups;
  private final List<FilePath> pdfFiles;
  private final BiConsumer<BibData, BibData> doneHndlr;
  private final boolean queryCrossref, queryGoogle;
  private final HashSet<String> alreadyCheckedIDs = new HashSet<>();

  public BibDataRetriever(AsyncHttpClient httpClient, BibData workBD, WorkTypeEnum workTypeEnum, List<ObjectGroup> authorGroups,
                          List<FilePath> pdfFiles, BiConsumer<BibData, BibData> doneHndlr)
  {
    this(httpClient, workBD, workTypeEnum, authorGroups, pdfFiles, true, true, doneHndlr);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private BibDataRetriever(AsyncHttpClient httpClient, BibData workBD, WorkTypeEnum workTypeEnum, List<ObjectGroup> authorGroups,
                           List<FilePath> pdfFiles, boolean queryCrossref, boolean queryGoogle, BiConsumer<BibData, BibData> doneHndlr)
  {
    this.workBD = workBD;
    this.httpClient = httpClient;
    this.workTypeEnum = workTypeEnum;
    this.authorGroups = authorGroups;
    this.pdfFiles = pdfFiles;
    this.doneHndlr = doneHndlr;
    this.queryCrossref = queryCrossref;
    this.queryGoogle = queryGoogle;

    doStage(1);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibDataRetriever forCrossref(AsyncHttpClient httpClient, BibData workBD, List<ObjectGroup> authorGroups,
                                             Consumer<BibData> doneHndlr)
  {
    return new BibDataRetriever(httpClient, workBD, wtNone, authorGroups, null, true, false, (pdfBD, queryBD) -> doneHndlr.accept(queryBD));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static BibDataRetriever forGoogleBooks(AsyncHttpClient httpClient, BibData workBD, List<ObjectGroup> authorGroups,
                                                Consumer<BibData> doneHndlr)
  {
    return new BibDataRetriever(httpClient, workBD, wtNone, authorGroups, null, false, true, (pdfBD, queryBD) -> doneHndlr.accept(queryBD));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void stop()
  {
    httpClient.stop();
    stopped = true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void finish(Exception e)
  {
    if (stopped) return;
    stop();

    boolean messageShown = false;

    if (e != null)
    {
      if ((e instanceof ParseException) || (e instanceof TerminateTaskException))
        noOp();
      else if (e instanceof UnknownHostException)
      {
        messageDialog("Unable to connect to host: " + e.getMessage(), mtError);
        messageShown = true;
      }
      else if (e instanceof HttpResponseException)
      {
        messageDialog(e.getMessage(), mtError);
        messageShown = true;
      }
      else
      {
        messageDialog("Error: " + e.getMessage(), mtError);
        messageShown = true;
      }
    }

    if ((queryBD == null) && (pdfBD == null) && (messageShown == false) && (collEmpty(pdfFiles) == false) && queryCrossref && queryGoogle)
      falseWithWarningMessage("Unable to acquire bibliographic information from PDF files or online sources.");

    doneHndlr.accept(pdfBD, queryBD);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void doStage(int stage)
  {
    if (queryBD != null)
    {
      finish(null);
      return;
    }

    if (stage < 2)
    {
      //   if there are 1 or more PDF files
      //     get PDF bib info

      if (collEmpty(pdfFiles) == false) try
      {
        pdfBD = PDFBibData.createFromFiles(pdfFiles);
      }
      catch (IOException e)
      {
        messageDialog("An error occurred while extracting metadata: " + e.getMessage(), mtError, true);
      }

      //   if there is a DOI
      //     if can get bib info from DOI
      //       exit

      if (queryCrossref)
      {
        String doi = workBD.getStr(bfDOI);
        if (doi.length() > 0)
        {
          if (stopped) return;

          CrossrefBibData.doHttpRequest(httpClient, doi, alreadyCheckedIDs, bd ->
          {
            queryBD = bd;
            doStage(2);
          }, this::finish);
          return;
        }
      }
    }

    if (stage < 3)
    {
      //   if have PDF bib info
      //     if PDF bib info has DOI
      //       if can get bib info from DOI
      //         exit

      if (queryCrossref)
      {
        String doi = pdfBD != null ? pdfBD.getStr(bfDOI) : "";
        if (doi.length() > 0)
        {
          if (stopped) return;

          CrossrefBibData.doHttpRequest(httpClient, doi, alreadyCheckedIDs, bd ->
          {
            queryBD = bd;
            doStage(3);
          }, this::finish);
          return;
        }
      }
    }

    String title = ultraTrim(workBD.getStr(bfTitle));
    if (title.isBlank())
      title = pdfBD != null ? ultraTrim(pdfBD.getStr(bfTitle)) : "";

    if (stage < 4)
    {
      //   if this is a newer book or a non-book
      //     use title, year, and authors to query Crossref for DOI and bib info
      //     if got bib info
      //       exit

      String yearStr = workBD.getStr(bfYear);
      if ((yearStr.length() > 0) && StringUtils.isNumeric(yearStr))
      {
        int year = parseInt(workBD.getStr(bfYear), -1);

        if (queryCrossref && (title.length() > 0) && ((workTypeEnum != wtBook) || (year >= 2000)))
        {
          if (stopped) return;

          CrossrefBibData.doHttpRequest(httpClient, title, workBD.getStr(bfYear), authorGroups, "", alreadyCheckedIDs, bd ->
          {
            searchedCrossref = true;
            queryBD = bd;
            doStage(4);
          }, this::finish);
          return;
        }
      }
    }

    if (stage < 5)
    {
      //   if this is a book or there is no work type
      //     if there are 1 or more ISBNs
      //       if can use existing ISBNs to get bib info
      //         exit

      List<String> isbns = null;
      if (queryGoogle && ((workTypeEnum == wtNone) || (workTypeEnum == wtBook)))
      {
        isbns = workBD.getMultiStr(bfISBNs);

        if (collEmpty(isbns) == false)
        {
          if (stopped) return;

          GoogleBibData.doHttpRequest(httpClient, isbns.iterator(), alreadyCheckedIDs, bd ->
          {
            queryBD = bd;
            doStage(5);
          }, this::finish);
          return;
        }
      }
    }

    if (stage < 6)
    {
      //     if have PDF bib info
      //       if PDF bib info has ISBN(s)
      //         if can use existing ISBNs to get bib info
      //           exit

      List<String> isbns = null;
      if (queryGoogle && ((workTypeEnum == wtNone) || (workTypeEnum == wtBook)))
      {
        isbns = pdfBD != null ? pdfBD.getMultiStr(bfISBNs) : null;
        if (collEmpty(isbns) == false)
        {
          if (stopped) return;

          GoogleBibData.doHttpRequest(httpClient, isbns.iterator(), alreadyCheckedIDs, bd ->
          {
            queryBD = bd;
            doStage(6);
          }, this::finish);
          return;
        }
      }
    }

    if (title.isBlank())
      finish(null);

    if (stage < 7)
    {
      //     use title and authors to query Google for ISBN and bib info
      //     if got bib info
      //       exit

      if (queryGoogle && ((workTypeEnum == wtNone) || (workTypeEnum == wtBook)))
      {
        if (stopped) return;

        GoogleBibData.doHttpRequest(httpClient, title, authorGroups, null, alreadyCheckedIDs, bd ->
        {
          queryBD = bd;
          doStage(7);
        }, this::finish);
        return;
      }
    }

    //   if didn't try to do so earlier,
    //     use title, year, and authors to query Crossref for DOI and bib info

    if (queryCrossref && (searchedCrossref == false))
    {
      if (stopped) return;

      CrossrefBibData.doHttpRequest(httpClient, workBD.getStr(bfTitle), workBD.getStr(bfYear), authorGroups, "", alreadyCheckedIDs, bd ->
      {
        queryBD = bd;
        finish(null);
      }, this::finish);
      return;
    }

    finish(null);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
