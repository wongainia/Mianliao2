package com.tjut.mianliao.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.tjut.mianliao.MainActivity;
import com.tjut.mianliao.NotificationHelper;
import com.tjut.mianliao.R;
import com.tjut.mianliao.XGMessageReceiver;
import com.tjut.mianliao.XGMessageReceiver.OnPublicNumPushListener;
import com.tjut.mianliao.XGMessageReceiver.OnTaskFinishPushListener;
import com.tjut.mianliao.component.BlockButton.BlockButtonClickListener;
import com.tjut.mianliao.component.LightDialog;
import com.tjut.mianliao.component.PopupView;
import com.tjut.mianliao.component.PopupView.OnItemClickListener;
import com.tjut.mianliao.component.PopupView.PopupItem;
import com.tjut.mianliao.component.ProImageView;
import com.tjut.mianliao.component.SearchView.OnClearIconClickListener;
import com.tjut.mianliao.component.SearchView.OnSearchTextListener;
import com.tjut.mianliao.contact.AddContactActivity;
import com.tjut.mianliao.contact.CheckableContactsActivity;
import com.tjut.mianliao.contact.ContactActivity;
import com.tjut.mianliao.contact.ContactUpdateCenter;
import com.tjut.mianliao.contact.ContactUpdateCenter.ContactObserver;
import com.tjut.mianliao.contact.ContactUpdateCenter.UpdateType;
import com.tjut.mianliao.contact.NewContactActivity;
import com.tjut.mianliao.contact.SubscriptionHelper;
import com.tjut.mianliao.contact.SubscriptionHelper.NewFriendsRequestListener;
import com.tjut.mianliao.contact.SystemNotifyInfoActivity;
import com.tjut.mianliao.contact.UserEntryManager;
import com.tjut.mianliao.contact.UserInfoManager;
import com.tjut.mianliao.data.AccountInfo;
import com.tjut.mianliao.data.ChatRecord;
import com.tjut.mianliao.data.DataHelper;
import com.tjut.mianliao.data.GroupInfo;
import com.tjut.mianliao.data.contact.UserEntry;
import com.tjut.mianliao.data.contact.UserInfo;
import com.tjut.mianliao.data.push.PushMessage;
import com.tjut.mianliao.forum.nova.ForumRemindActivity;
import com.tjut.mianliao.forum.nova.MessageRemindManager;
import com.tjut.mianliao.forum.nova.MessageRemindManager.MessageRemindListener;
import com.tjut.mianliao.main.TabFragment;
import com.tjut.mianliao.news.NewsListActivity;
import com.tjut.mianliao.profile.MyReplyActivity;
import com.tjut.mianliao.settings.Settings;
import com.tjut.mianliao.util.StringUtils;
import com.tjut.mianliao.util.Utils;
import com.tjut.mianliao.xmpp.ChatHelper;
import com.tjut.mianliao.xmpp.ChatHelper.MessageReceiveListener;
import com.tjut.mianliao.xmpp.ChatHelper.MessageSendListener;
import com.tjut.mianliao.xmpp.ConnectionManager;
import com.tjut.mianliao.xmpp.ConnectionManager.ConnectionObserver;

public class RecentChatsTabFragment extends TabFragment implements AdapterView.OnItemClickListener,
        OnItemLongClickListener, View.OnClickListener, DialogInterface.OnClickListener, ContactObserver,
        ConnectionObserver, MessageReceiveListener, MessageSendListener, NewFriendsRequestListener,
        OnTaskFinishPushListener, OnPublicNumPushListener, MessageRemindListener {

    private enum RecordType {
        TYPE_RECORD, TYPE_NOTICE
    }

    private static final String TAG = "RecentChatsTabFragment";

    private static final long VIBRATE_MILLIS = 250;

    private ListView mRecentChatsListView;

    private UserEntryManager mUserEntryManager;
    private UserInfoManager mUserInfoManager;
    private UnreadMessageHelper mUnreadMessageHelper;
    private ConnectionManager mConnectionManager;
    private ChatHelper mChatHelper;
    private SubscriptionHelper mSubscriptionHelper;

    private RecentChatAdapter mAdapter;
    private ArrayList<ChatRecord> mRecentChats = new ArrayList<ChatRecord>();
    private ArrayList<ChatRecord> mDisplayChats;
    private PresenceComparator mComparator;
    private LightDialog mDeleteDialog;
    private String mDelTarget;
    private int mDelType;
    private String mMyAccount;
    private boolean mIsSearching = false;
    private String mSearchStr;
    private ChatRecord mChatRecord;

    private SharedPreferences mPreferences;

    private NotificationHelper mNotificationHelper;

    private PopupView mTitlePopupView;
    private ImageButton mIBtRight;

    private Context appCtx;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            contentUpdated();
        };
    };
    
    @Override
    public int getLayoutId() {
        return R.layout.main_tab_recent_chat;
    }

    @Override
    public int getNaviButtonId() {
        return R.id.nb_chat;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appCtx = getActivity().getApplicationContext();
        mPreferences = DataHelper.getSpForData(appCtx);
        mChatHelper = ChatHelper.getInstance(appCtx);
        mChatHelper.registerReceiveListener(this);
        mChatHelper.registerSendListener(this);
        mNotificationHelper = NotificationHelper.getInstance(getActivity());
        mUserEntryManager = UserEntryManager.getInstance(appCtx);
        mConnectionManager = ConnectionManager.getInstance(appCtx);
        mSubscriptionHelper = SubscriptionHelper.getInstance(appCtx);
        mSubscriptionHelper.registerNewFriendsRequestListener(this);
        mUserInfoManager = UserInfoManager.getInstance(appCtx);
        mUnreadMessageHelper = UnreadMessageHelper.getInstance(appCtx);
        MessageRemindManager.registerMessageRemindListener(this);
        XGMessageReceiver.registerOnTaskFinishPushListener(this);
        XGMessageReceiver.registerOnPublicNumListener(this);
        mUserInfoManager.loadUserInfo();
        mMyAccount = AccountInfo.getInstance(appCtx).getAccount();
        mAdapter = new RecentChatAdapter();
        mDisplayChats = mRecentChats;
        mComparator = new PresenceComparator();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mTitleBar.showTitleText(R.string.tab_chat, null);
        mTitleBar.showLeftButton(R.drawable.icon_personal, this);
        mTitleBar.showRightButton(R.drawable.icon_more, this);

        mIBtRight = (ImageButton) view.findViewById(R.id.btn_right);
        mRecentChatsListView = (ListView) view.findViewById(R.id.lv_recent_contacts);
        mRecentChatsListView.setAdapter(mAdapter);
        mRecentChatsListView.setOnItemClickListener(this);
        mRecentChatsListView.setOnItemLongClickListener(this);
        mConnectionManager.registerConnectionObserver(this);
        ContactUpdateCenter.registerObserver(this);
        mHandler.sendMessage(Message.obtain());
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroy();
        mRecentChatsListView.setAdapter(null);
        ContactUpdateCenter.removeObserver(this);
        mConnectionManager.unregisterConnectionObserver(this);
    }

    @Override
    public void onDestroy() {
        mChatHelper.unregisterReceiveListener(this);
        mChatHelper.unregisterSendListener(this);
        mSubscriptionHelper.unregisterNewFriendsRequestListener(this);
        super.onDestroy();
    }

    @Override
    public void onTabButtonClicked() {
        super.onTabButtonClicked();
        refreshData();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        removeRecord();
        contentUpdated();
    }

    private void removeRecord() {
        if (mSubscriptionHelper.getCount() <= 0) {
            if (mRecentChats.contains(mChatRecord)) {
                mChatRecord = null;
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ChatRecord cr = (ChatRecord) parent.getItemAtPosition(position);
        if (cr != null) {
            switch (cr.msgType) {
                case ChatRecord.MSG_TYPE_ADD_FRIENDS_REQUEST:
                    startActivity(NewContactActivity.class);
                    break;
                case ChatRecord.MSG_TYPE_SYS:
                    startActivity(SystemNotifyInfoActivity.class);
                    break;
                case ChatRecord.MSG_TYPE_CHAT:
                    Intent i = new Intent(getActivity(), ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_CHAT_TARGET, cr.target);
                    i.putExtra(ChatActivity.EXTRA_SHOW_PROFILE, true);
                    i.putExtra(ChatActivity.EXTRA_CHAT_ISGOUPCHAT, cr.isGroupChat);
                    i.putExtra(ChatActivity.EXTRA_GROUPCHAT_ID, cr.groupId);
                    startActivity(i);
                    break;
                case ChatRecord.MSG_TYPE_PUBLIC_NUMBER:
                    Intent intent = new Intent(getActivity(), NewsListActivity.class);
                    intent.putExtra(NewsListActivity.EXTRA_ID, cr.publicId);
                    intent.putExtra(NewsListActivity.EXTRA_NAME, cr.from);
                    startActivity(intent);
                    break;
                case ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT:
                    mUnreadMessageHelper.setMessageTarget(UnreadMessageHelper.TARGET_REPLY_AT);
                	Intent remindIntent = new Intent(getActivity(), ForumRemindActivity.class);
                	remindIntent.putExtra(ForumRemindActivity.EXT_MESSAGE_TYPE,
                	        MessageRemindManager.MessageType.TYPE_AT_USER.ordinal());
                	startActivity(remindIntent);
                	break;
                case ChatRecord.MSG_TYPE_UP_DOWN:
                    mUnreadMessageHelper.setMessageTarget(UnreadMessageHelper.TARGET_HATE_LIKE);
                    Intent remindIntent2 = new Intent(getActivity(), ForumRemindActivity.class);
                    remindIntent2.putExtra(ForumRemindActivity.EXT_MESSAGE_TYPE,
                            MessageRemindManager.MessageType.TYPE_NOTICE.ordinal());
                    startActivity(remindIntent2);
                	break;
                default:
                    break;
            }

        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        ChatRecord cr = (ChatRecord) parent.getItemAtPosition(position);
        if (cr != null) {
            if (!canDel(cr)) {
                return true;
            }
            if (cr.msgType == 3) {
                mDelType = 0;
            } else {
                mDelType = 1;
            }
            mDelTarget = cr.target;
            showDeleteDialog(cr.isGroupChat);
            return true;
        } else {
            return false;
        }

    }

    private boolean canDel(ChatRecord record) {
        switch (record.msgType) {
            case ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT:
            case ChatRecord.MSG_TYPE_SYS:
            case ChatRecord.MSG_TYPE_UP_DOWN:
                return false;
            default:
                return true;
        }
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_right:
                showTitlePopupMenu(mIBtRight);
                break;
            case R.id.btn_left:
                MainActivity.showDrawerLayout();
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == 0) {
            if (mDelType == 0) {
                DataHelper.deleteChatRecords(getActivity(), 3);
                for (int i = 0; i < mDisplayChats.size(); i++) {
                    if (mDisplayChats.get(i).msgType == 3) {
                        mDisplayChats.remove(i);
                    }
                }
                mAdapter.notifyDataSetChanged();
            } else {
                mUnreadMessageHelper.deleteChat(mDelTarget);
                if (deleteChat(mDelTarget)) {
                    contentUpdated();
                }
            }
        }
    }

    @Override
    public void onContactsUpdated(final UpdateType type, final Object data) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (type) {
                    case Unsubscribe:
                        if (data instanceof String && deleteChat((String) data)) {
                            contentUpdated();
                        }
                        break;
                    case Presence:
                    case UserInfo:
                        contentUpdated();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void onConnectionUpdated(int state) {
        switch (state) {
            case ConnectionManager.EMCHAT_CONNECTED:
                mTitleBar.hideProgress();
                mTitleBar.hideLeftText();
                break;
            case ConnectionManager.EMCHAT_DISCONNECTED:
            default:
                mTitleBar.hideProgress();
                mTitleBar.showLeftText(R.string.disconnected, null);
                break;
        }
    }

    @Override
    public void onMessageReceived(ChatRecord record) {
        processRecord(record);
    }

    @Override
    public void onMessageReceiveFailed(ChatRecord record) {
    }

    @Override
    public void onMessagePreSend(ChatRecord record) {
        processRecord(record);
    }

    @Override
    public void onMessageSent(ChatRecord record) {
    }

    @Override
    public void onMessageSendFailed(ChatRecord record) {
    }

    private boolean deleteChat(String target) {
        for (int i = 0; i < mRecentChats.size(); i++) {
            if (mRecentChats.get(i).isTarget(target)) {
                return mRecentChats.remove(i) != null;
            }
        }

        return false;
    }

    private void processRecord(ChatRecord record) {
        if (record.isChatState || !mChatHelper.processRecord(record) || isLiveMessage(record)) {
            return;
        }

        deleteChat(record.target);
        mRecentChats.add(0, record);
        mUserInfoManager.acquireUserInfo(record.target);
        contentUpdated();
    }

    private boolean isLiveMessage(ChatRecord record) {
        return record.groupId != null && record.groupId.contains("live");
    }

    private void contentUpdated() {
        Context context = getActivity();
        if (context == null) {
            return;
        }
        mRecentChats = new ArrayList<ChatRecord>(DataHelper.loadRecentChats(context));
        if (mChatRecord != null) {
            UserInfo info = null;
            if (mChatRecord.target != null) {
                info = mUserInfoManager.getUserInfo(mChatRecord.target);
            }

            if (info != null || mChatRecord.msgType == ChatRecord.MSG_TYPE_SYS) {
                if (info != null) {
                    mChatRecord.text = info.getDisplayName(getActivity()) + getString(R.string.nc_request_desc);
                }

                mRecentChats.add(0, mChatRecord);
            }
        }
        if (mRecentChats.isEmpty()) {
            mRecentChats = recentfilter(mRecentChats);
            mAdapter.notifyDataSetChanged();
        } else {
            mRecentChatsListView.setVisibility(View.VISIBLE);
            sortPresence();
            mRecentChats = recentfilter(mRecentChats);
            mAdapter.notifyDataSetChanged();
        }
        processSearch();
    }

    private ArrayList<ChatRecord> recentfilter(ArrayList<ChatRecord> mRecentChats) {
        ArrayList<ChatRecord> filterRecord = new ArrayList<>();
        for (ChatRecord record : mRecentChats) {
            if (!record.isNightRecord  && !isLiveMessage(record)) {
                filterRecord.add(record);
            }
        }
        addNoticeMsgRecord(filterRecord);
        return filterRecord;
    }

    private void addNoticeMsgRecord(ArrayList<ChatRecord> records) {
        records.add(0, createNoticeChatRecord(ChatRecord.MSG_TYPE_SYS));
        records.add(0, createNoticeChatRecord(ChatRecord.MSG_TYPE_UP_DOWN));
        records.add(0, createNoticeChatRecord(ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT));
        boolean hasNewRequestRecord = false;
        for (ChatRecord record : records) {
            if (record.msgType == ChatRecord.MSG_TYPE_ADD_FRIENDS_REQUEST) {
                hasNewRequestRecord = true;
            }
        }
        ArrayList<String> subRequests = mSubscriptionHelper.getSubRequests();
        if (subRequests.size() > 0) {
            String target = subRequests.get(subRequests.size() - 1);
            if (!hasNewRequestRecord) {
                mChatRecord = createNewFriendRecord(target);
                replaceRecord();
            }
        } else {
            removeRecord();
        }
    }

    private ChatRecord createNoticeChatRecord(int type) {
        ChatRecord record = new ChatRecord();
        record.msgType = type;
        return record;
    }

    private void sortPresence() {
        Collections.sort(mRecentChats, mComparator);
    }

    private void processSearch() {
        if (!mIsSearching) {
            mDisplayChats = mRecentChats;
            mAdapter.notifyDataSetChanged();
            return;
        }
        mDisplayChats = new ArrayList<ChatRecord>();
        if (getActivity() == null) {
            return;
        }
        for (ChatRecord record : mRecentChats) {
            UserInfo userInfo = mUserInfoManager.getUserInfo(record.target);
            if ((!TextUtils.isEmpty(record.from) && record.from.contains(mSearchStr))
                    || (!TextUtils.isEmpty(record.target) && record.target.contains(mSearchStr))
                    || record.text.contains(mSearchStr) || isContainNick(userInfo)) {
                mDisplayChats.add(record);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private boolean isContainNick(UserInfo userInfo) {
        if (userInfo == null || getActivity() == null) {
            return false;
        }
        return userInfo.getDisplayName(getActivity()).contains(mSearchStr);
    }

    private void showDeleteDialog(boolean isGroupChat) {
        if (mDeleteDialog == null) {
            mDeleteDialog = new LightDialog(getActivity());
            mDeleteDialog.setItems(R.array.cht_long_press_menu, this);
        }
        if (isGroupChat) {
            GroupInfo groupInfo = DataHelper.loadGroupInfo(getActivity(),
                    StringUtils.parseName(mDelTarget));
            if (groupInfo != null) {
                mDeleteDialog.setTitle(groupInfo.groupName);
            } else {
                mDeleteDialog.setTitle(R.string.group_chat_title);
            }
        } else {
            mDeleteDialog.setTitle(mUserInfoManager.getUserName(mDelTarget));
        }
        mDeleteDialog.show();
    }

    OnSearchTextListener mSearchListener = new OnSearchTextListener() {

        @Override
        public void onSearchTextChanged(CharSequence text) {
            if (TextUtils.isEmpty(text)) {
                mIsSearching = false;
            } else {
                mSearchStr = text.toString();
                mIsSearching = true;
            }

            processSearch();
        }

    };

    OnClearIconClickListener mSearchClearListener = new OnClearIconClickListener() {

        @Override
        public void onClickClearIcon() {
            mIsSearching = false;

        }

    };

    BlockButtonClickListener mBlockButtonListener = new BlockButtonClickListener() {

        @Override
        public void onClickLeftButton() {
            startActivity(new Intent(getActivity(), ContactActivity.class));
        }

        @Override
        public void onClickRightButton() {
            startActivity(new Intent(getActivity(), AddContactActivity.class));
        }

    };

    private class RecentChatAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mDisplayChats.size();
        }

        @Override
        public ChatRecord getItem(int position) {
            return mDisplayChats.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDisplayChats.get(position).id;
        }

        @Override
        public int getViewTypeCount() {
            return RecordType.values().length;
        }

        @Override
        public int getItemViewType(int position) {
            ChatRecord record = getItem(position);
            switch (record.msgType) {
                case ChatRecord.MSG_TYPE_SYS:
                case ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT:
                case ChatRecord.MSG_TYPE_UP_DOWN:
                case ChatRecord.MSG_TYPE_PUBLIC_NUMBER:
                    return RecordType.TYPE_NOTICE.ordinal();
                default:
                    return RecordType.TYPE_RECORD.ordinal();
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup root) {
            RecordType type = RecordType.values()[getItemViewType(position)];
            View view;
            if (convertView != null) {
                view = convertView;
            } else {
                view = inflateView(type, root);
            }

            ChatRecord cr = mDisplayChats.get(position);

            TextView tvTime = (TextView) view.findViewById(R.id.tv_time);
            TextView tvName = (TextView) view.findViewById(R.id.tv_contact_name);
            ProImageView ivAvatar = (ProImageView) view.findViewById(R.id.iv_contact_avatar);

            TextView tvUnreadBadge = (TextView) view.findViewById(R.id.tv_unread_badge);
            ImageView ivTypeIcon = (ImageView) view.findViewById(R.id.iv_type_icon);
            UserInfo userInfo;
            switch (cr.msgType) {
                case ChatRecord.MSG_TYPE_ADD_FRIENDS_REQUEST:
                    userInfo = mUserInfoManager.getUserInfo(cr.target);
                    tvName.setText(getString(R.string.nc_title));
                    Utils.setText(view, R.id.tv_message, cr.text);
                    int count = mUnreadMessageHelper.getCount(
                            UnreadMessageHelper.TARGET_ADD_FRIEND);
                    tvUnreadBadge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    String unCount = count < 100 ? String.valueOf(count) : "99+";
                    tvUnreadBadge.setText(unCount);
                    ivAvatar.setImageResource(R.drawable.chat_pic_bg_new_friend);
                    if (userInfo == null) {
                        ivAvatar.setImageResource(cr.isGroupChat ? R.drawable.chat_pic_bg_wechat
                                : R.drawable.chat_botton_bg_faviconboy);
                    } else {
                        ivAvatar.setImage(userInfo.getAvatar(), userInfo.defaultAvatar());
                        // update type icon; it while show in day time ;or it
                        // should hide
                        int resIcon = userInfo.getTypeIcon();
                        if (resIcon > 0) {
                            ivTypeIcon.setImageResource(resIcon);
                            ivTypeIcon.setVisibility(View.VISIBLE);
                        } else {
                            ivTypeIcon.setVisibility(View.GONE);
                        }
                    }
                    break;
                case ChatRecord.MSG_TYPE_SYS:
                    tvName.setText(getString(R.string.ntc_sys_msg));
                    count = mUnreadMessageHelper.getCount(UnreadMessageHelper.TARGET_SYS_INFO);
                    tvUnreadBadge.setVisibility(View.VISIBLE);
                    unCount = count < 100 ? String.valueOf(count) : "99+";
                    tvUnreadBadge.setText(unCount);
                    ivAvatar.setImageResource(R.drawable.pic_system);
                    tvUnreadBadge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    break;
                case ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT:
                	tvName.setText(getString(R.string.ntc_at_reply_comment));
                    count = mUnreadMessageHelper.getCount(UnreadMessageHelper.TARGET_REPLY_AT);
                    tvUnreadBadge.setVisibility(View.VISIBLE);
                    unCount = count < 100 ? String.valueOf(count) : "99+";
                    tvUnreadBadge.setText(unCount);
                    ivAvatar.setImageResource(R.drawable.pic_message);
                    tvUnreadBadge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    break;
                case ChatRecord.MSG_TYPE_UP_DOWN:
                	tvName.setText(getString(R.string.ntc_up_down));
                    count = mUnreadMessageHelper.getCount(UnreadMessageHelper.TARGET_HATE_LIKE);
                    tvUnreadBadge.setVisibility(View.VISIBLE);
                    unCount = count < 100 ? String.valueOf(count) : "99+";
                    tvUnreadBadge.setText(unCount);
                    ivAvatar.setImageResource(R.drawable.pic_nice);
                    tvUnreadBadge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    break;
                case ChatRecord.MSG_TYPE_CHAT:
                    String message;
                    if (cr.isFrom(mMyAccount)) {
                        message = getString(R.string.me) + cr.text;
                    } else {
                        message = cr.text;
                    }
                    Utils.setText(view, R.id.tv_message, message);

                    tvTime.setText(Utils.getTimeDesc(cr.timestamp));
                    tvTime.setVisibility(View.VISIBLE);
                    userInfo = mUserInfoManager.getUserInfo(cr.target);
                    if (userInfo == null || getActivity() == null) {
                        mUserInfoManager.acquireUserInfo(cr.target);
                        tvName.setText(getString(R.string.field_no_info));
                    } else {
                        tvName.setText(userInfo.getDisplayName(getActivity()));
                        view.findViewById(R.id.iv_vip_bg).setVisibility(
                                userInfo.vip ? View.VISIBLE : View.GONE);
                    }

                    if (cr.isGroupChat) {
                        tvName.setText(R.string.group_chat_title);
                        GroupInfo groupInfo = null;
                        try {
                            groupInfo = DataHelper.loadGroupInfo(getActivity(), cr.groupId);
                            if (groupInfo != null) {
                                tvName.setText(groupInfo.groupName);
                            } else {
                                tvName.setText(R.string.group_chat_title);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (userInfo == null) {
                        ivAvatar.setImageResource(cr.isGroupChat ? R.drawable.chat_pic_bg_wechat
                                : R.drawable.chat_botton_bg_faviconboy);
                    } else {
                        ivAvatar.setImage(userInfo.getAvatar(), userInfo.defaultAvatar());
                        // update type icon; it will show in day time ;or it
                        // should hide
                        int resIcon = userInfo.getTypeIcon();
                        if (resIcon > 0) {
                            ivTypeIcon.setImageResource(resIcon);
                            ivTypeIcon.setVisibility(View.VISIBLE);
                        } else {
                            ivTypeIcon.setVisibility(View.GONE);
                        }
                    }

                    int unreadMessage = mUnreadMessageHelper.getCount(cr.target);

                    if (unreadMessage > 0) {
                        tvUnreadBadge.setVisibility(View.VISIBLE);
                        unCount = unreadMessage < 100 ? String.valueOf(unreadMessage) : "99+";
                        tvUnreadBadge.setText(unCount);
                    } else {
                        tvUnreadBadge.setVisibility(View.INVISIBLE);
                    }

                    break;
                case ChatRecord.MSG_TYPE_PUBLIC_NUMBER:
                    tvName.setText(cr.from);
                    tvTime.setVisibility(View.GONE);
                    count = mUnreadMessageHelper.getCount(UnreadMessageHelper.getPublicNumTarget(cr.publicId));
                    unCount = count < 100 ? String.valueOf(count) : "99+";
                    tvUnreadBadge.setText(unCount);
                    tvUnreadBadge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
                    if (TextUtils.isEmpty(cr.url)) {
                        ivAvatar.setImageResource(R.drawable.chat_pic_bg_new_friend);
                    } else {
                        ivAvatar.setImage(cr.url, R.drawable.chat_pic_bg_new_friend);
                    }
                    break;
                default:
                    break;
            }
//            if (position == getCount() - 1) {
//                view.findViewById(R.id.view_line).setVisibility(View.GONE);
//            } else {
                view.findViewById(R.id.view_line).setVisibility(View.VISIBLE);
//            }

            return view;
        }

    }

    private class PresenceComparator implements Comparator<ChatRecord> {
        @Override
        public int compare(ChatRecord lhs, ChatRecord rhs) {
            if (lhs.msgType == ChatRecord.MSG_TYPE_PUBLIC_NUMBER) {
                return -3;
            } else {
                boolean lhsIsTop = mPreferences.getBoolean(
                        GroupChatInfoActivity.IS_TO_TOP + StringUtils.parseName(lhs.target), false);
                boolean rhsIsTop = mPreferences.getBoolean(
                        GroupChatInfoActivity.IS_TO_TOP + StringUtils.parseName(rhs.target), false);
                if (lhsIsTop) {
                    return -2;
                }
                if (rhsIsTop) {
                    return 2;
                }
                return (int) Math.signum(rhs.timestamp - lhs.timestamp);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 1) {
            Intent chatIntent = new Intent(getActivity(), ChatActivity.class);
            ArrayList<UserEntry> users = data.getParcelableArrayListExtra(AddFriendToGroupActivity.EXTRA_RESULT);
            chatIntent.putParcelableArrayListExtra(AddFriendToGroupActivity.EXTRA_RESULT, users);
            chatIntent.putExtra(ChatActivity.EXTRA_SHOW_PROFILE, true);
            startActivity(chatIntent);

        }
    }

    public void updateNoticeView(View view, ChatRecord cr) {

    }

    public void updateChatRecordView(View view, ChatRecord cr) {
        updateBaseInfo(view, cr);
    }

    private void updateBaseInfo(View view, ChatRecord cr) {
        TextView tvName = (TextView) view.findViewById(R.id.tv_contact_name);
        switch (cr.msgType) {
            case ChatRecord.MSG_TYPE_AT_REPLEY_COMMENT:
                tvName.setText(getString(R.string.ntc_at_reply_comment));
                break;
            case ChatRecord.MSG_TYPE_PUBLIC_NUMBER:
                tvName.setText(cr.from);
                break;
            case ChatRecord.MSG_TYPE_SYS:
                tvName.setText(getString(R.string.ntc_sys_msg));
                break;
            case ChatRecord.MSG_TYPE_UP_DOWN:
                tvName.setText(getString(R.string.ntc_up_down));
                break;
            default:
                break;
        }
    }

    public View inflateView(RecordType type, ViewGroup root) {
        switch (type) {
            case TYPE_RECORD:
                return mInflater.inflate(R.layout.list_item_recent_chat, root, false);
            default:
                return mInflater.inflate(R.layout.list_item_recent_chat_notice, root, false);
        }
    }

    @Override
    public void onNewRequest(final String target) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (target == null) {
                    return;
                }
                mChatRecord = createNewFriendRecord(target);
                if (replaceRecord()) {
                    mAdapter.notifyDataSetChanged();
                    contentUpdated();
                } else {
                    processRecord(mChatRecord);
                }
                notice();
            }
        });
    }

    private boolean replaceRecord() {
        boolean replaced = false;
        for (ChatRecord record : mDisplayChats) {
            if (record.msgType == mChatRecord.msgType
                    || ((record.msgType == ChatRecord.MSG_TYPE_PUBLIC_NUMBER)
                            && record.publicId == mChatRecord.publicId)) {
                record = mChatRecord;
                replaced = true;
                return replaced;
            }
        }
        return replaced;
    }

    private ChatRecord createNewFriendRecord(String target) {
        ChatRecord record = new ChatRecord();
        record.isChatState = false;
        record.timestamp = System.currentTimeMillis();
        record.target = target;
        record.msgType = ChatRecord.MSG_TYPE_ADD_FRIENDS_REQUEST;
        UserInfo info = mUserInfoManager.getUserInfo(target);
        if (info != null && getActivity() != null) {
            record.text = info.getDisplayName(getActivity()) + getString(R.string.nc_request_desc);
        } else {
            record.text = getString(R.string.nc_request_desc);   
        }
        return record;
    }

    @Override
    public void onTaskFinished(final PushMessage mesage) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChatRecord = createTaskRecord(mesage);
                if (replaceRecord()) {
                    mAdapter.notifyDataSetChanged();
                } else {
                    processRecord(mChatRecord);
                }
                notice();
            }
        });
    }

    @Override
    public void OnPublicNumPush(final OfficialAccountInfo mesage) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mesage == null || mesage.id <= 0) {
                    return;
                }
                mChatRecord = createPublicNumRecord(mesage);
                ChatRecord mDbChatRecord = DataHelper.loadChatRecordbyMsgType(getActivity(),
                        ChatRecord.MSG_TYPE_PUBLIC_NUMBER, mChatRecord.publicId);
                if (mDbChatRecord == null) {
                    DataHelper.insertChatRecord(getActivity(), mChatRecord);
                } else {
                    DataHelper.updateChatRecordbyMsgType(getActivity(), mChatRecord);
                }

                if (replaceRecord()) {
                    mAdapter.notifyDataSetChanged();
                } else {
                    mChatRecord = null;
                    contentUpdated();
                }
                notice();
            }
        });
    }

    private ChatRecord createTaskRecord(PushMessage mesage) {
        ChatRecord record = new ChatRecord();
        record.isChatState = false;
        record.timestamp = System.currentTimeMillis();
        record.text = mesage.getPushTaskMessage().getContent();
        record.msgType = ChatRecord.MSG_TYPE_SYS;
        return record;
    }

    private ChatRecord createPublicNumRecord(OfficialAccountInfo mesage) {
        ChatRecord record = new ChatRecord();
        record.isChatState = false;
        record.timestamp = System.currentTimeMillis();
        record.text = mesage.content;
        record.publicId = mesage.id;
        record.from = mesage.name;
        record.url = mesage.vatar;
        record.msgType = ChatRecord.MSG_TYPE_PUBLIC_NUMBER;
        return record;
    }

    private void notice() {
        if (getActivity() == null) {
            return;
        }
        Settings settings = Settings.getInstance(getActivity());
        AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (settings.allowNewMessageVibrate() && (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT)) {
            ((Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VIBRATE_MILLIS);
        }
        sendNotification(mChatRecord);
    }

    private void sendNotification(ChatRecord record) {
        if (getActivity() == null) {
            return;
        }
        String content = getContentByType(record);
        String title = getTitleByType(record);
        switch (record.msgType) {
            case ChatRecord.MSG_TYPE_ADD_FRIENDS_REQUEST:
                mNotificationHelper.sendNewFriendNotification(title, content, record);
                break;
            case ChatRecord.MSG_TYPE_PUBLIC_NUMBER:
                mNotificationHelper.sendPublicNumNotification(title, content, record);
                break;
            default:
                mNotificationHelper.sendSystemNoticeNotification(title, content, record);
                break;
        }
    }

    private String getTitleByType(ChatRecord record) {
        if (record.msgType == ChatRecord.MSG_TYPE_SYS) {
            return getString(R.string.ntc_sys_msg);
        } else if (record.msgType == ChatRecord.MSG_TYPE_PUBLIC_NUMBER) {
            return getString(R.string.public_num_title);
        }
        return getString(R.string.nc_new_friend);
    }

    private String getContentByType(ChatRecord record) {
        if (record.msgType == ChatRecord.MSG_TYPE_SYS) {
            return getString(R.string.cht_task_finish,record.text);
        }
        return record.text;
    }

    private void showTitlePopupMenu(View anchor) {
        if (mTitlePopupView == null) {
            mTitlePopupView = new PopupView(getActivity());
            mTitlePopupView.setItems(R.array.recent_chat_day_popup, mOnItemClicklisten);
        }
        mTitlePopupView.showAsDropDown(anchor, true);
    }

    private OnItemClickListener mOnItemClicklisten = new com.tjut.mianliao.component.PopupView.OnItemClickListener() {

        @Override
        public void onItemClick(int position, PopupItem item) {
            switch (position) {
                case 0:
                    startActivity(new Intent(getActivity(), ContactActivity.class));
                    break;
                case 1:
                    Intent intent = new Intent(getActivity(), AddFriendToGroupActivity.class);
                    intent.putExtra(CheckableContactsActivity.EXTRA_IS_SET_RESULT, true);
                    startActivityForResult(intent, 100);
                    break;
                case 2:
                    startActivity(new Intent(getActivity(), AddContactActivity.class));
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void hasNewMessage(String target) {
        mAdapter.notifyDataSetChanged();
    }

}
