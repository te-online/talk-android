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

package com.nextcloud.talk.newarch.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.chat.ChatMessage

class ChatMessageConverter {
  @TypeConverter
  fun fromChatMessageToString(chatMessage: ChatMessage?): String {
    if (chatMessage == null) {
      return ""
    } else {
      return LoganSquare.serialize(chatMessage)
    }
  }

  @TypeConverter
  fun fromStringToChatMessage(value: String): ChatMessage? {
    return LoganSquare.parse(value, ChatMessage::class.java)
  }
}