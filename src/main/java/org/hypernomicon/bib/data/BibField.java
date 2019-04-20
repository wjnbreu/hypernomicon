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

package org.hypernomicon.bib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;
import static org.hypernomicon.bib.BibData.BibFieldType.*;
import static org.hypernomicon.bib.BibUtils.*;

import org.hypernomicon.bib.BibData.BibFieldEnum;
import org.hypernomicon.bib.BibData.BibFieldType;

public class BibField
{
  private final BibFieldEnum bibFieldEnum;
  private final BibFieldType type;
  private final List<String> strList = new ArrayList<>();

  private String str;

  public BibField(BibFieldEnum bibFieldEnum)
  {
    this.bibFieldEnum = bibFieldEnum;

    type = BibData.getFieldType(bibFieldEnum);
  }

  public boolean isMultiStr() { return type == bftMultiString; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void setStr(String newStr)
  {
    if (type != bftString)
    {
      messageDialog("Internal error #90225", mtError);
      return;
    }

    str = newStr;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public String getStr()
  {
    if (type == bftString)
      return safeStr(str);

    switch (bibFieldEnum)
    {
      case bfContainerTitle: case bfTitle:

        return buildTitle(strList);

      case bfMisc:

        return strListToStr(strList, false, true);

      default:
        messageDialog("Internal error #90227", mtError);
        return null;
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static String buildTitle(List<String> list)
  {
    StringBuilder sb = new StringBuilder();

    list.forEach(titleStr ->
    {
      titleStr = titleStr.trim();

      if (titleStr.length() == 0) return;

      if (sb.length() > 0)
      {
        if (StringUtils.isAlpha(StringUtils.right(sb.toString(), 1)))
          sb.append(":");

        sb.append(" ");
      }

      sb.append(titleStr);
    });

    return sb.toString();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void setAll(List<String> otherList)
  {
    if (type != bftMultiString)
    {
      messageDialog("Internal error #90230", mtError);
      return;
    }

    strList.clear();
    strList.addAll(otherList);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public List<String> getMultiStr()
  {
    if (type != bftMultiString)
    {
      messageDialog("Internal error #90231", mtError);
      return null;
    }

    return Collections.unmodifiableList(strList);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void clear()
  {
    if (type == bftMultiString)
      strList.clear();
    else
      str = "";
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void addStr(String newStr)
  {
    if (type != bftMultiString)
    {
      messageDialog("Internal error #90229", mtError);
      return;
    }

    if (ultraTrim(safeStr(newStr)).length() == 0) return;

    switch (bibFieldEnum)
    {
      case bfISBNs : matchISBN(newStr, strList); break;
      case bfISSNs : matchISSN(newStr, strList); break;
      default      : strList.add(newStr);        break;
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}