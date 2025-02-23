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
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.DUMMY
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.GUEST
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.MODERATOR
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.OWNER
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.USER
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType.USER_FOLLOWING_LINK

class ParticipantTypeConverter {
  @TypeConverter
  fun fromParticipantType(participantType: ParticipantType): Int {
    return participantType.ordinal
  }

  @TypeConverter
  fun fromIntToParticipantType(value: Int): ParticipantType {
    when (value) {
      0 -> return DUMMY
      1 -> return OWNER
      2 -> return MODERATOR
      3 -> return USER
      4 -> return GUEST
      else -> return USER_FOLLOWING_LINK

    }
  }
}