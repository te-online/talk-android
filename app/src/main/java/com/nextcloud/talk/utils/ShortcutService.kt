/*
 * Nextcloud Talk application
 *
 * @author Thomas Ebert
 * Copyright (C) 2017-2019 Thomas Ebert <thomas@thomasebert.net>
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

package com.nextcloud.talk.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.newarch.domain.repository.NextcloudTalkOfflineRepository
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.models.json.conversations.Conversation
import java.util.*

class ShortcutService constructor(
        private val shortcutManager: ShortcutManager,
        private val context: Context,
        private val mainActivity: MainActivity
): DefaultLifecycleObserver {
    private var shortcuts: MutableList<ShortcutInfo> = ArrayList()
    private val currentUserLiveData: MutableLiveData<UserEntity> = MutableLiveData()
    private lateinit var offlineRepository: NextcloudTalkOfflineRepository
    private val conversationsLiveData = Transformations.switchMap(currentUserLiveData) {
        offlineRepository.getConversationsForUser(it.id)
    }

    fun registerShortcuts() {
        val openNewConverationIntent = Intent(context, MainActivity::class.java)
        openNewConverationIntent.action = BundleKeys.KEY_NEW_CONVERSATION
        openNewConverationIntent.putExtra ("new", true)

        shortcuts.add(ShortcutInfo.Builder(context, "new")
                // @TODO: I18n
                .setShortLabel("New")
                .setLongLabel("New conversation")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_add_white_24px))
                .setIntent(openNewConverationIntent)
                .build())

        shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_3")
                // @TODO: I18n
                .setShortLabel("New")
                .setLongLabel("3rd latest conv")
                .setIcon(Icon.createWithResource(context, R.drawable.accent_circle))
                .setIntent(openNewConverationIntent)
                .build())

        shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_2")
                // @TODO: I18n
                .setShortLabel("New")
                .setLongLabel("2nd latest conv")
                .setIcon(Icon.createWithResource(context, R.drawable.accent_circle))
                .setIntent(openNewConverationIntent)
                .build())

        shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_1")
                // @TODO: I18n
                .setShortLabel("New")
                .setLongLabel("First latest conv")
                .setIcon(Icon.createWithResource(context, R.drawable.accent_circle, R.drawable.ic_add_white_24px))
                .setIntent(openNewConverationIntent)
                .build())

        // Get latest 3 conversations from database here
        // Add shortcut for each of these conversations
        conversationsLiveData.observe(mainActivity, Observer<List<Conversation>> {
            val sortedConversationsList = it.toMutableList()

            /*sortedConversationsList.sortWith(Comparator { conversation1, conversation2 ->
                CompareToBuilder()
                        .append(conversation2.favorite, conversation1.favorite)
                        .append(conversation2.lastActivity, conversation1.lastActivity)
                        .toComparison()
            })*/
            Log.d("conversation", sortedConversationsList.toString())

            var i = 0
            for (conversation in sortedConversationsList) {
                if(i > 3) continue
                i++
                // @TODO: Find conversation shortcut here and replace
                Log.d("conversation", conversation.toString())
            }
        })

        this.shortcutManager!!.dynamicShortcuts.clear();
        this.shortcutManager!!.dynamicShortcuts = shortcuts
    }
}