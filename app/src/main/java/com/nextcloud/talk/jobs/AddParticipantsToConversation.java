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

package com.nextcloud.talk.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;
import org.greenrobot.eventbus.EventBus;

@AutoInjector(NextcloudTalkApplication.class)
public class AddParticipantsToConversation extends Worker {
  @Inject
  NcApi ncApi;

  @Inject
  UserUtils userUtils;

  @Inject
  EventBus eventBus;

  public AddParticipantsToConversation(@NonNull Context context,
      @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
    NextcloudTalkApplication.Companion.getSharedApplication()
        .getComponentApplication()
        .inject(this);
  }

  @NonNull
  @Override
  public Result doWork() {
    Data data = getInputData();
    String[] selectedUserIds = data.getStringArray(BundleKeys.INSTANCE.getKEY_SELECTED_USERS());
    String[] selectedGroupIds = data.getStringArray(BundleKeys.INSTANCE.getKEY_SELECTED_GROUPS());
    UserEntity user = userUtils.getUserWithInternalId(
        data.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), -1));
    String conversationToken = data.getString(BundleKeys.INSTANCE.getKEY_TOKEN());
    String credentials = ApiUtils.getCredentials(user.getUsername(), user.getToken());

    RetrofitBucket retrofitBucket;
    for (String userId : selectedUserIds) {
      retrofitBucket =
          ApiUtils.getRetrofitBucketForAddParticipant(user.getBaseUrl(), conversationToken,
              userId);

      ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
          .subscribeOn(Schedulers.io())
          .blockingSubscribe();
    }

    for (String groupId : selectedGroupIds) {
      retrofitBucket =
          ApiUtils.getRetrofitBucketForAddGroupParticipant(user.getBaseUrl(), conversationToken,
              groupId);

      ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
          .subscribeOn(Schedulers.io())
          .blockingSubscribe();
    }

    eventBus.post(new EventStatus(user.getId(), EventStatus.EventType.PARTICIPANTS_UPDATE, true));
    return Result.success();
  }
}
