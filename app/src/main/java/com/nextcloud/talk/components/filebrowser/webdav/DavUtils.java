/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.components.filebrowser.webdav;

import at.bitfire.dav4jvm.Property;
import at.bitfire.dav4jvm.PropertyFactory;
import at.bitfire.dav4jvm.PropertyRegistry;
import at.bitfire.dav4jvm.property.CreationDate;
import at.bitfire.dav4jvm.property.DisplayName;
import at.bitfire.dav4jvm.property.GetContentLength;
import at.bitfire.dav4jvm.property.GetContentType;
import at.bitfire.dav4jvm.property.GetETag;
import at.bitfire.dav4jvm.property.GetLastModified;
import at.bitfire.dav4jvm.property.ResourceType;
import com.nextcloud.talk.components.filebrowser.models.properties.NCEncrypted;
import com.nextcloud.talk.components.filebrowser.models.properties.NCPreview;
import com.nextcloud.talk.components.filebrowser.models.properties.OCFavorite;
import com.nextcloud.talk.components.filebrowser.models.properties.OCId;
import com.nextcloud.talk.components.filebrowser.models.properties.OCSize;
import java.util.ArrayList;
import java.util.List;

public class DavUtils {
  public static final String OC_NAMESPACE = "http://owncloud.org/ns";
  public static final String NC_NAMESPACE = "http://nextcloud.org/ns";
  public static final String DAV_PATH = "/remote.php/dav/files/";

  public static final String EXTENDED_PROPERTY_NAME_PERMISSIONS = "permissions";
  public static final String EXTENDED_PROPERTY_NAME_REMOTE_ID = "id";
  public static final String EXTENDED_PROPERTY_NAME_SIZE = "size";
  public static final String EXTENDED_PROPERTY_FAVORITE = "favorite";
  public static final String EXTENDED_PROPERTY_IS_ENCRYPTED = "is-encrypted";
  public static final String EXTENDED_PROPERTY_MOUNT_TYPE = "mount-type";
  public static final String EXTENDED_PROPERTY_OWNER_ID = "owner-id";
  public static final String EXTENDED_PROPERTY_OWNER_DISPLAY_NAME = "owner-display-name";
  public static final String EXTENDED_PROPERTY_UNREAD_COMMENTS = "comments-unread";
  public static final String EXTENDED_PROPERTY_HAS_PREVIEW = "has-preview";
  public static final String EXTENDED_PROPERTY_NOTE = "note";
  public static final String TRASHBIN_FILENAME = "trashbin-filename";
  public static final String TRASHBIN_ORIGINAL_LOCATION = "trashbin-original-location";
  public static final String TRASHBIN_DELETION_TIME = "trashbin-deletion-time";

  public static final String PROPERTY_QUOTA_USED_BYTES = "quota-used-bytes";
  public static final String PROPERTY_QUOTA_AVAILABLE_BYTES = "quota-available-bytes";

  static Property.Name[] getAllPropSet() {
    List<Property.Name> propSet = new ArrayList<>();

    propSet.add(DisplayName.NAME);
    propSet.add(GetContentType.NAME);
    propSet.add(GetContentLength.NAME);
    propSet.add(GetContentType.NAME);
    propSet.add(GetContentLength.NAME);
    propSet.add(GetLastModified.NAME);
    propSet.add(CreationDate.NAME);
    propSet.add(GetETag.NAME);
    propSet.add(ResourceType.NAME);

    propSet.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_NAME_PERMISSIONS));
    propSet.add(OCId.NAME);
    propSet.add(OCSize.NAME);
    propSet.add(OCFavorite.NAME);
    propSet.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_OWNER_ID));
    propSet.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_OWNER_DISPLAY_NAME));
    propSet.add(new Property.Name(OC_NAMESPACE, EXTENDED_PROPERTY_UNREAD_COMMENTS));

    propSet.add(NCEncrypted.NAME);
    propSet.add(new Property.Name(NC_NAMESPACE, EXTENDED_PROPERTY_MOUNT_TYPE));
    propSet.add(NCPreview.NAME);
    propSet.add(new Property.Name(NC_NAMESPACE, EXTENDED_PROPERTY_NOTE));

    return propSet.toArray(new Property.Name[0]);
  }

  public static void registerCustomFactories() {
    PropertyRegistry propertyRegistry = PropertyRegistry.INSTANCE;
    List<PropertyFactory> propertyFactories = new ArrayList<>();
    propertyFactories.add(new OCId.Factory());
    propertyFactories.add(new NCPreview.Factory());
    propertyFactories.add(new NCEncrypted.Factory());
    propertyFactories.add(new OCFavorite.Factory());
    propertyFactories.add(new OCSize.Factory());

    propertyRegistry.register(propertyFactories);
  }
}
