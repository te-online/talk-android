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

package com.nextcloud.talk.components.filebrowser.models.properties;

import android.text.TextUtils;
import at.bitfire.dav4jvm.Property;
import at.bitfire.dav4jvm.PropertyFactory;
import at.bitfire.dav4jvm.XmlUtils;
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class NCPreview implements Property {
  public static final Property.Name NAME =
      new Property.Name(DavUtils.NC_NAMESPACE, DavUtils.EXTENDED_PROPERTY_HAS_PREVIEW);

  @Getter
  @Setter
  private boolean ncPreview;

  private NCPreview(boolean hasPreview) {
    ncPreview = hasPreview;
  }

  public static class Factory implements PropertyFactory {

    @Nullable
    @Override
    public Property create(@NotNull XmlPullParser xmlPullParser) {
      try {
        String text = XmlUtils.INSTANCE.readText(xmlPullParser);
        if (!TextUtils.isEmpty(text)) {
          return new NCPreview(Boolean.parseBoolean(text));
        }
      } catch (IOException e) {
        e.printStackTrace();
      } catch (XmlPullParserException e) {
        e.printStackTrace();
      }

      return new OCFavorite(false);
    }

    @NotNull
    @Override
    public Property.Name getName() {
      return NAME;
    }
  }
}
