/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.controllers;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.jobs.CapabilitiesWorker;
import com.nextcloud.talk.jobs.PushRegistrationWorker;
import com.nextcloud.talk.jobs.SignalingSettingsWorker;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.RoomsOverall;
import com.nextcloud.talk.models.json.generic.Status;
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall;
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListView;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;
import com.uber.autodispose.AutoDispose;
import io.reactivex.CompletableObserver;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.net.CookieManager;
import javax.inject.Inject;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

@AutoInjector(NextcloudTalkApplication.class)
public class AccountVerificationController extends BaseController {

  public static final String TAG = "AccountVerificationController";

  @Inject
  NcApi ncApi;

  @Inject
  UserUtils userUtils;

  @Inject
  CookieManager cookieManager;

  @Inject
  AppPreferences appPreferences;

  @Inject
  EventBus eventBus;

  @BindView(R.id.progress_text)
  TextView progressText;

  private long internalAccountId = -1;

  private String baseUrl;
  private String username;
  private String token;
  private boolean isAccountImport;
  private String originalProtocol;

  public AccountVerificationController(Bundle args) {
    super();
    if (args != null) {
      baseUrl = args.getString(BundleKeys.INSTANCE.getKEY_BASE_URL());
      username = args.getString(BundleKeys.INSTANCE.getKEY_USERNAME());
      token = args.getString(BundleKeys.INSTANCE.getKEY_TOKEN());
      if (args.containsKey(BundleKeys.INSTANCE.getKEY_IS_ACCOUNT_IMPORT())) {
        isAccountImport = true;
      }
      if (args.containsKey(BundleKeys.INSTANCE.getKEY_ORIGINAL_PROTOCOL())) {
        originalProtocol = args.getString(BundleKeys.INSTANCE.getKEY_ORIGINAL_PROTOCOL());
      }
    }
  }

  @Override
  protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    return inflater.inflate(R.layout.controller_account_verification, container, false);
  }

  @Override
  protected void onDetach(@NonNull View view) {
    eventBus.unregister(this);
    super.onDetach(view);
  }

  @Override
  protected void onAttach(@NonNull View view) {
    super.onAttach(view);
    eventBus.register(this);
  }

  @Override
  protected void onViewBound(@NonNull View view) {
    super.onViewBound(view);
    NextcloudTalkApplication.Companion.getSharedApplication()
        .getComponentApplication()
        .inject(this);

    if (getActivity() != null) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    if (getActionBar() != null) {
      getActionBar().hide();
    }

    if (isAccountImport && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")
        || (!TextUtils
        .isEmpty(originalProtocol) && !baseUrl.startsWith(originalProtocol))) {
      determineBaseUrlProtocol(true);
    } else {
      checkEverything();
    }
  }

  private void checkEverything() {
    String credentials = ApiUtils.getCredentials(username, token);
    cookieManager.getCookieStore().removeAll();

    findServerTalkApp(credentials);
  }

  private void determineBaseUrlProtocol(boolean checkForcedHttps) {
    cookieManager.getCookieStore().removeAll();

    String queryUrl;

    baseUrl = baseUrl.replace("http://", "").replace("https://", "");

    if (checkForcedHttps) {
      queryUrl = "https://" + baseUrl + ApiUtils.getUrlPostfixForStatus();
    } else {
      queryUrl = "http://" + baseUrl + ApiUtils.getUrlPostfixForStatus();
    }

    ncApi.getServerStatus(queryUrl)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .as(AutoDispose.autoDisposable(getScopeProvider()))
        .subscribe(new Observer<Status>() {
          @Override
          public void onSubscribe(Disposable d) {
          }

          @Override
          public void onNext(Status status) {
            if (checkForcedHttps) {
              baseUrl = "https://" + baseUrl;
            } else {
              baseUrl = "http://" + baseUrl;
            }

            if (isAccountImport) {
              getRouter().replaceTopController(
                  RouterTransaction.with(new WebViewLoginController(baseUrl,
                      false, username, ""))
                      .pushChangeHandler(new HorizontalChangeHandler())
                      .popChangeHandler(new HorizontalChangeHandler()));
            } else {
              checkEverything();
            }
          }

          @Override
          public void onError(Throwable e) {
            if (checkForcedHttps) {
              determineBaseUrlProtocol(false);
            } else {
              abortVerification();
            }
          }

          @Override
          public void onComplete() {

          }
        });
  }

  private void findServerTalkApp(String credentials) {
    ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(baseUrl))
        .subscribeOn(Schedulers.io())
        .as(AutoDispose.autoDisposable(getScopeProvider()))
        .subscribe(new Observer<RoomsOverall>() {
          @Override
          public void onSubscribe(Disposable d) {
          }

          @Override
          public void onNext(RoomsOverall roomsOverall) {
            fetchProfile(credentials);
          }

          @Override
          public void onError(Throwable e) {
            if (getActivity() != null && getResources() != null) {
              getActivity().runOnUiThread(
                  () -> progressText.setText(String.format(getResources().getString(
                      R.string.nc_nextcloud_talk_app_not_installed),
                      getResources().getString(R.string.nc_app_name))));
            }

            ApplicationWideMessageHolder.getInstance().setMessageType(
                ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK);

            abortVerification();
          }

          @Override
          public void onComplete() {

          }
        });
  }

  private void storeProfile(String displayName, String userId) {
    userUtils.createOrUpdateUser(username, token,
        baseUrl, displayName, null, true,
        userId, null, null,
        appPreferences.getTemporaryClientCertAlias(), null)
        .subscribeOn(Schedulers.io())
        .as(AutoDispose.autoDisposable(getScopeProvider()))
        .subscribe(new Observer<UserEntity>() {
          @Override
          public void onSubscribe(Disposable d) {
          }

          @Override
          public void onNext(UserEntity userEntity) {
            internalAccountId = userEntity.getId();

            if (new ClosedInterfaceImpl().isGooglePlayServicesAvailable()) {
              registerForPush();
            } else {
              getActivity().runOnUiThread(
                  () -> progressText.setText(progressText.getText().toString() + "\n" +
                      getResources().getString(R.string.nc_push_disabled)));
              fetchAndStoreCapabilities();
            }
          }

          @Override
          public void onError(Throwable e) {
            progressText.setText(progressText.getText().toString() +
                "\n" +
                getResources().getString(
                    R.string.nc_display_name_not_stored));
            abortVerification();
          }

          @Override
          public void onComplete() {

          }
        });
  }

  private void fetchProfile(String credentials) {
    ncApi.getUserProfile(credentials,
        ApiUtils.getUrlForUserProfile(baseUrl))
        .subscribeOn(Schedulers.io())
        .as(AutoDispose.autoDisposable(getScopeProvider()))
        .subscribe(new Observer<UserProfileOverall>() {
          @Override
          public void onSubscribe(Disposable d) {
          }

          @Override
          public void onNext(UserProfileOverall userProfileOverall) {
            String displayName = null;
            if (!TextUtils.isEmpty(userProfileOverall.getOcs().getData()
                .getDisplayName())) {
              displayName = userProfileOverall.getOcs().getData()
                  .getDisplayName();
            } else if (!TextUtils.isEmpty(userProfileOverall.getOcs().getData()
                .getDisplayNameAlt())) {
              displayName = userProfileOverall.getOcs().getData()
                  .getDisplayNameAlt();
            }

            if (!TextUtils.isEmpty(displayName)) {
              storeProfile(displayName, userProfileOverall.getOcs().getData().getUserId());
            } else {
              if (getActivity() != null) {
                getActivity().runOnUiThread(
                    () -> progressText.setText(progressText.getText().toString() + "\n" +
                        getResources().getString(R.string.nc_display_name_not_fetched)));
              }
              abortVerification();
            }
          }

          @Override
          public void onError(Throwable e) {
            if (getActivity() != null) {
              getActivity().runOnUiThread(
                  () -> progressText.setText(progressText.getText().toString() + "\n" +
                      getResources().getString(R.string.nc_display_name_not_fetched)));
            }
            abortVerification();
          }

          @Override
          public void onComplete() {

          }
        });
  }

  private void registerForPush() {
    OneTimeWorkRequest pushRegistrationWork =
        new OneTimeWorkRequest.Builder(PushRegistrationWorker.class).build();
    WorkManager.getInstance().enqueue(pushRegistrationWork);
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  public void onMessageEvent(EventStatus eventStatus) {
    if (eventStatus.getEventType().equals(EventStatus.EventType.PUSH_REGISTRATION)) {
      if (internalAccountId == eventStatus.getUserId()
          && !eventStatus.isAllGood()
          && getActivity() != null) {
        getActivity().runOnUiThread(
            () -> progressText.setText(progressText.getText().toString() + "\n" +
                getResources().getString(R.string.nc_push_disabled)));
      }
      fetchAndStoreCapabilities();
    } else if (eventStatus.getEventType().equals(EventStatus.EventType.CAPABILITIES_FETCH)) {
      if (internalAccountId == eventStatus.getUserId() && !eventStatus.isAllGood()) {
        if (getActivity() != null) {
          getActivity().runOnUiThread(
              () -> progressText.setText(progressText.getText().toString() + "\n" +
                  getResources().getString(R.string.nc_capabilities_failed)));
        }
        abortVerification();
      } else if (internalAccountId == eventStatus.getUserId() && eventStatus.isAllGood()) {
        fetchAndStoreExternalSignalingSettings();
      }
    } else if (eventStatus.getEventType().equals(EventStatus.EventType.SIGNALING_SETTINGS)) {
      if (internalAccountId == eventStatus.getUserId() && !eventStatus.isAllGood()) {
        if (getActivity() != null) {
          getActivity().runOnUiThread(
              () -> progressText.setText(progressText.getText().toString() + "\n" +
                  getResources().getString(R.string.nc_external_server_failed)));
        }
      }

      proceedWithLogin();
    }
  }

  private void fetchAndStoreCapabilities() {
    Data userData = new Data.Builder()
        .putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), internalAccountId)
        .build();

    OneTimeWorkRequest pushNotificationWork =
        new OneTimeWorkRequest.Builder(CapabilitiesWorker.class)
            .setInputData(userData)
            .build();
    WorkManager.getInstance().enqueue(pushNotificationWork);
  }

  private void fetchAndStoreExternalSignalingSettings() {
    Data userData = new Data.Builder()
        .putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), internalAccountId)
        .build();

    OneTimeWorkRequest signalingSettings =
        new OneTimeWorkRequest.Builder(SignalingSettingsWorker.class)
            .setInputData(userData)
            .build();
    WorkManager.getInstance().enqueue(signalingSettings);
  }

  private void proceedWithLogin() {
    cookieManager.getCookieStore().removeAll();
    userUtils.disableAllUsersWithoutId(internalAccountId);

    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        if (userUtils.getUsers().size() == 1) {
          getRouter().setRoot(RouterTransaction.with(new
              ConversationsListView())
              .pushChangeHandler(new HorizontalChangeHandler())
              .popChangeHandler(new HorizontalChangeHandler()));
        } else {
          if (isAccountImport) {
            ApplicationWideMessageHolder.getInstance().setMessageType(
                ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED);
          }
          getRouter().popToRoot();
        }
      });
    }
  }

  @Override
  protected void onDestroyView(@NonNull View view) {
    super.onDestroyView(view);
    if (getActivity() != null) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }
  }

  private void abortVerification() {

    if (!isAccountImport) {
      if (internalAccountId != -1) {
        userUtils.deleteUserWithId(internalAccountId).subscribe(new CompletableObserver() {
          @Override
          public void onSubscribe(Disposable d) {

          }

          @Override
          public void onComplete() {
            if (getActivity() != null) {
              getActivity().runOnUiThread(() -> {
                new Handler().postDelayed(() -> getRouter().popToRoot(), 7500);
              });
            }
          }

          @Override
          public void onError(Throwable e) {

          }
        });
      } else {
        if (getActivity() != null) {
          getActivity().runOnUiThread(() -> {
            new Handler().postDelayed(() -> getRouter().popToRoot(), 7500);
          });
        }
      }
    } else {
      ApplicationWideMessageHolder.getInstance().setMessageType(
          ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT);
      if (getActivity() != null) {
        getActivity().runOnUiThread(() -> new Handler().postDelayed(() -> {
          if (getRouter().hasRootController()) {
            if (getActivity() != null) {
              getRouter().popToRoot();
            }
          } else {
            if (userUtils.anyUserExists()) {
              getRouter().setRoot(RouterTransaction.with(new ConversationsListView())
                  .pushChangeHandler(new HorizontalChangeHandler())
                  .popChangeHandler(new HorizontalChangeHandler()));
            } else {
              getRouter().setRoot(RouterTransaction.with(new ServerSelectionController())
                  .pushChangeHandler(new HorizontalChangeHandler())
                  .popChangeHandler(new HorizontalChangeHandler()));
            }
          }
        }, 7500));
      }
    }
  }
}
