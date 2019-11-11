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

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
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

    // @TODO: Make this a singleton

    @TargetApi(Build.VERSION_CODES.P)
    fun registerShortcuts() {
        val openNewConversationIntent = Intent(context, MainActivity::class.java)
        openNewConversationIntent.action = BundleKeys.KEY_NEW_CONVERSATION
        openNewConversationIntent.putExtra ("new", true)

        // Add a default shortcut to send a new conversation intent
        /*val newConversationShortcut = ShortcutInfo.Builder(context, "new")
                // @TODO: I18n
                .setShortLabel("New")
                .setLongLabel("New conversation")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                .setIntent(openNewConversationIntent)
                .build()*/

        // Subscribe to updates of model attribute `conversationLiveData`
        model = ViewModelProvider(activity, activity.factory).get(ConversationsListViewModel::class.java)
        model.apply {
            conversationsLiveData.observe(activity, Observer {
                val sortedConversationsList = it.toMutableList()

                // shortcuts = shortcutManager.dynamicShortcuts
                shortcuts = ArrayList()

                /*if(shortcuts.size > 0) {
                    shortcuts[0] = ShortcutInfo.Builder(context, "new")
                            // @TODO: I18n
                            .setShortLabel("New")
                            .setLongLabel("New conversation")
                            .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                            .setIntent(openNewConversationIntent)
                            .build()
                } else {*/
                    shortcuts.add(ShortcutInfo.Builder(context, "new")
                            // @TODO: I18n
                            .setShortLabel("New")
                            .setLongLabel("New conversation")
                            .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                            .setIntent(openNewConversationIntent)
                            .build())
                //}

                sortedConversationsList.sortWith(Comparator { conversation1, conversation2 ->
                    CompareToBuilder()
                            .append(conversation2.favorite, conversation1.favorite)
                            .append(conversation2.lastActivity, conversation1.lastActivity)
                            .toComparison()
                })

                Log.d("ShortcutService", sortedConversationsList.toString())

                // Add shortcut to latest 3 conversations
                for ((index, conversation) in sortedConversationsList.withIndex()) {
                    // Only do this for the first 3 conversations
                    if (index > 1) continue

                    // If shortcut does not exist, create it
                    Log.d("ShortcutService", shortcutManager.dynamicShortcuts.size.toString() + "/" + index.toString())
                    // if(shortcuts.size < index + 2) {
                        shortcuts.add(ShortcutInfo.Builder(context, "current_conversation_" + (index + 1))
                                .setShortLabel(conversation.displayName as String)
                                .setLongLabel(conversation.displayName as String)
                                // @TODO: Use avatar as icon
                                .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                                // @TODO: Create intent to open conversation
                                .setIntent(openNewConversationIntent)
                                .build())
                    /*} else {
                        // Shortcut exists, update it
                        var existingShortcut = shortcutManager.dynamicShortcuts[index + 1]
                        shortcuts[index + 1] = ShortcutInfo.Builder(context, existingShortcut.id)
                                .setShortLabel(conversation.displayName as String)
                                .setLongLabel(conversation.displayName as String)
                                // @TODO: Use avatar as icon
                                .setIcon(Icon.createWithResource(context, R.drawable.ic_add_grey600_24px))
                                // @TODO: Create intent to open conversation
                                .setIntent(openNewConversationIntent)
                                .build()
                    }*/
                    Log.d("ShortcutService", conversation.toString())
                }

                // shortcutManager!!.dynamicShortcuts.clear()
                // Only use first 3 items, even if there is maybe some old trash in the list
                // shortcutManager!!.dynamicShortcuts = shortcuts.slice(IntRange(0,3))
                shortcutManager!!.dynamicShortcuts = shortcuts
            })
        }
    }
}