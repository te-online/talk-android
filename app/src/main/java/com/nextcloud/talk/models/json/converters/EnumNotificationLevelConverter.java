/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;

public class EnumNotificationLevelConverter
    extends IntBasedTypeConverter<Conversation.NotificationLevel> {
  @Override
  public Conversation.NotificationLevel getFromInt(int i) {
    switch (i) {
      case 0:
        return Conversation.NotificationLevel.DEFAULT;
      case 1:
        return Conversation.NotificationLevel.ALWAYS;
      case 2:
        return Conversation.NotificationLevel.MENTION;
      case 3:
        return Conversation.NotificationLevel.NEVER;
      default:
        return Conversation.NotificationLevel.DEFAULT;
    }
  }

  @Override
  public int convertToInt(Conversation.NotificationLevel object) {
    switch (object) {
      case DEFAULT:
        return 0;
      case ALWAYS:
        return 1;
      case MENTION:
        return 2;
      case NEVER:
        return 3;
      default:
        return 0;
    }
  }
}
