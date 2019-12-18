/*
 * Nextcloud Talk application
 *
 * @author Thomas Ebert<thomas@thomasebert.net>
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

package com.nextcloud.talk.utils

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListView
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListViewModel
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.apache.commons.lang3.builder.CompareToBuilder
import java.util.*

class ShortcutService constructor(
        private val shortcutManager: ShortcutManager,
        private val context: Context,
        private val activity: ConversationsListView
) {
    private var shortcuts: MutableList<ShortcutInfo> = ArrayList()
    private lateinit var model: ConversationsListViewModel

    // @TODO: Make this a singleton?

    @TargetApi(Build.VERSION_CODES.P)
    fun registerShortcuts() {
        val openNewConversationIntent = Intent(context, MainActivity::class.java)
        openNewConversationIntent.action = BundleKeys.KEY_NEW_CONVERSATION
        openNewConversationIntent.putExtra ("new", true)

        // Subscribe to updates of model attribute `conversationLiveData`
        model = ViewModelProvider(activity, activity.factory).get(ConversationsListViewModel::class.java)
        model.apply {
            conversationsLiveData.observe(activity, Observer {
                val sortedConversationsList = it.toMutableList()

                shortcuts = ArrayList()

                shortcuts.add(ShortcutInfo.Builder(context, "new")
                        // @TODO: I18n
                        .setShortLabel("New")
                        .setLongLabel("New conversation")
                        .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                        .setIntent(openNewConversationIntent)
                        .build())

                sortedConversationsList.sortWith(Comparator { conversation1, conversation2 ->
                    CompareToBuilder()
                            .append(conversation2.favorite, conversation1.favorite)
                            .append(conversation2.lastActivity, conversation1.lastActivity)
                            .toComparison()
                })

                // Add shortcut to latest 3 conversations
                for ((index, conversation) in sortedConversationsList.withIndex()) {
                    // Only do this for the first 3 conversations
                    if (index > 1) continue

                    val intent = Intent(context, MainActivity::class.java)
                    intent.action = BundleKeys.KEY_NEW_CONVERSATION
                    intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
                    intent.putExtra(BundleKeys.KEY_ROOM_ID, conversation.conversationId)
                    intent.putExtra ("conversation", true)

                    // If shortcut does not exist, create it
                    shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_" + (index + 1))
                            .setShortLabel(conversation.displayName as String)
                            .setLongLabel(conversation.displayName as String)
                            // @TODO: Use avatar as icon
                            .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                            // @TODO: Create intent to open conversation
                            .setIntent(intent)
                            .build())
                }

                shortcutManager!!.dynamicShortcuts = shortcuts
            })
        }
    }
}