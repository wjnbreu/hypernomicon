/*
 * Copyright 2015-2020 Jason Winning
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

package org.hypernomicon.model.items;

import org.hypernomicon.model.HDI_Schema;
import org.hypernomicon.model.HyperDB.Tag;
import org.hypernomicon.model.records.RecordState;
import org.hypernomicon.model.records.RecordType;

import static org.hypernomicon.model.records.RecordType.*;
import static org.hypernomicon.util.Util.*;

import java.util.Map;

import static org.hypernomicon.model.HyperDB.*;

public class HDI_OfflinePointerSingle extends HDI_OfflineBase
{
  int objID = -1;
  private final RecordType objType;
  Map<Tag, HDI_OfflineBase> tagToNestedItem;

  public HDI_OfflinePointerSingle(HDI_Schema schema, RecordState recordState)
  {
    super(schema, recordState);
    objType = db.getObjType(schema.getRelType());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public int getObjID()           { return objID; }
  public void setObjID(int objID) { this.objID = objID; }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void setFromXml(Tag tag, String nodeText, RecordType objType, int objID, Map<Tag, HDI_OfflineBase> nestedItems)
  {
    this.objID = objID;

    if (collEmpty(nestedItems) == false)
      tagToNestedItem = nestedItems;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override public void writeToXml(Tag tag, StringBuilder xml)
  {
    if (objID < 1) return;

    if (tagToNestedItem != null)
      writePointerTagWithNestedPointers(xml, tag, objID, db.records(objType).getByID(objID).getXMLObjectName(), tagToNestedItem);
    else
      writePointerTag(xml, tag, objID, hdtNone, db.records(objType).getByID(objID).getXMLObjectName());
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
