package com.tjut.mianliao.tribe;

import java.util.ArrayList;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener2;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.squareup.picasso.Picasso;
import com.tjut.mianliao.BaseActivity;
import com.tjut.mianliao.R;
import com.tjut.mianliao.component.AvatarView;
import com.tjut.mianliao.component.TitleBar;
import com.tjut.mianliao.data.AccountInfo;
import com.tjut.mianliao.data.tribe.TribeInfo;
import com.tjut.mianliao.data.tribe.TribeTypeInfo;
import com.tjut.mianliao.util.JsonUtil;
import com.tjut.mianliao.util.MsRequest;
import com.tjut.mianliao.util.MsResponse;
import com.tjut.mianliao.util.MsTask;

public class TribeClassifyDetailActivity extends BaseActivity implements
		OnClickListener, OnRefreshListener2<ListView> {

	public static final String EXT_TRIBE_TYPE_DATA = "ext_tribe_type_data";
	public static final String EXT_TRIBE_TYPE = "ext_tribe_type";
	public static final String EXT_TRIBE_BY_UID = "ext_tribe_by_uid";
	
	public static final int EXT_TYPE_ALL_TRIBE = 100111;
	public static final int EXT_TYPE_MY_FOLLOW_TRIBE = 100112;
	
	@ViewInject(R.id.ptlv_tribe_classify_detail)
	private PullToRefreshListView mPtrClassifyDetail;

	private TribeTypeInfo mTribeTypeInfo;
	private ArrayList<TribeInfo> mTribes;
	private TribeConcerAdapter mAdapter;
	private int tribeType, mUserId;

	private TitleBar mTitleBar;

	@Override
	protected int getLayoutResID() {
		return R.layout.activity_tribe_classify_detail;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ViewUtils.inject(this);
		mTribes = new ArrayList<TribeInfo>();
		mTitleBar = getTitleBar();
		mTribeTypeInfo = getIntent().getParcelableExtra(EXT_TRIBE_TYPE_DATA);
		tribeType = getIntent().getIntExtra(EXT_TRIBE_TYPE, 0);
		mUserId = getIntent().getIntExtra(EXT_TRIBE_BY_UID, 0);
		if (mTribeTypeInfo != null) {
		    mTitleBar.setTitle(mTribeTypeInfo.name);
		} else if (mUserId > 0){
		    mTitleBar.setTitle(getString(R.string.tribe_follow_tribe));
		}else {
            mTitleBar.setTitle(getString(R.string.tribe_all_tribe));
        }
 		mAdapter = new TribeConcerAdapter();
		mPtrClassifyDetail.setMode(Mode.BOTH);
		mPtrClassifyDetail.setOnRefreshListener(this);
		mPtrClassifyDetail.setAdapter(mAdapter);
		mPtrClassifyDetail.setRefreshing(Mode.PULL_FROM_START);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		fetchTribes(true);
	}

	private class TribeConcerAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return mTribes.size();
		}

		@Override
		public TribeInfo getItem(int position) {
			return mTribes.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TribeConcernViewHoder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(
						R.layout.list_item_tribe_classify, parent, false);
				holder = new TribeConcernViewHoder();
				ViewUtils.inject(holder, convertView);
				convertView.setTag(holder);
			} else {
				holder = (TribeConcernViewHoder) convertView.getTag();
			}
			TribeInfo tribe = getItem(position);
			Picasso.with(TribeClassifyDetailActivity.this).load(tribe.icon)
					.into(holder.mIvTribeLogo);
			holder.mTvTribeName.setText(tribe.tribeName);
			holder.mTvTribeDesc.setText(tribe.tribeDesc);
			holder.mTvPostNum.setText(getString(
					R.string.tribe_is_followed_count_post, tribe.threadCount));
			holder.mTvPeopleNum.setText(getString(
					R.string.tribe_is_followed_count, tribe.followCount));
			holder.mTribe = tribe;
			holder.mTvIsConcern
					.setOnClickListener(TribeClassifyDetailActivity.this);
			holder.mTvIsConcern.setTag(tribe);
			if (tribe.collected) {
				holder.mTvIsConcern
						.setText(getString(R.string.tribe_is_followed));
				holder.mTvIsConcern.setCompoundDrawablesWithIntrinsicBounds(
						R.drawable.channel_icon_ok, 0, 0, 0);
				holder.mTvIsConcern
						.setBackgroundResource(R.drawable.bg_tv_gray);
				holder.mTvIsConcern.setTextColor(0xffaae3ff);
				holder.mTvIsConcern.setEnabled(false);
			} else {
				holder.mTvIsConcern
						.setText(getString(R.string.tribe_collected_add));
				holder.mTvIsConcern.setCompoundDrawablesWithIntrinsicBounds(0,
						0, 0, 0);
				holder.mTvIsConcern
						.setBackgroundResource(R.drawable.bg_tv_blue);
				holder.mTvIsConcern.setTextColor(Color.WHITE);
				holder.mTvIsConcern.setEnabled(true);
			}
			convertView.setOnClickListener(TribeClassifyDetailActivity.this);
			return convertView;
		}

	}

	private OnClickListener mTribeCencernOnclick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			TribeConcernViewHoder holder = (TribeConcernViewHoder) v.getTag();
			TribeInfo tribe = holder.mTribe;
			Intent itn = new Intent(TribeClassifyDetailActivity.this,
					TribeDetailActivity.class);
			itn.putExtra(TribeInfo.INTENT_EXTRA_INFO, tribe);
			startActivity(itn);
		}
	};

	private class TribeConcernViewHoder {
		@ViewInject(R.id.piv_tribe_logo)
		AvatarView mIvTribeLogo;
		@ViewInject(R.id.tv_tribe_name)
		TextView mTvTribeName;
		@ViewInject(R.id.tv_tribe_describe)
		TextView mTvTribeDesc;
		@ViewInject(R.id.tv_post_num)
		TextView mTvPostNum;
		@ViewInject(R.id.tv_people_num)
		TextView mTvPeopleNum;
		@ViewInject(R.id.tv_is_concern)
		TextView mTvIsConcern;
		@ViewInject(R.id.line_horizonal)
		View mLine;
		TribeInfo mTribe;
	}

	private class GetTribeTypeTask extends MsTask {

		private int mOffset;

		public GetTribeTypeTask(int offset) {
			super(TribeClassifyDetailActivity.this,
					MsRequest.TRIBE_LIST_BY_TYPE);
			mOffset = offset;
		}

		@Override
		protected String buildParams() {
			return new StringBuilder("tribe_type=").append(mTribeTypeInfo.type)
					.append("&offset=").append(mOffset).toString();
		}

		@Override
		protected void onPostExecute(MsResponse response) {
			mPtrClassifyDetail.onRefreshComplete();
			if (response.isSuccessful()) {
				if (mOffset <= 0) {
					mTribes.clear();
				}
				ArrayList<TribeInfo> tribes = JsonUtil.getArray(
						response.getJsonArray(), TribeInfo.TRANSFORMER);
				mTribes.addAll(tribes);
				mAdapter.notifyDataSetChanged();
			}
		}

	}
	
	private class GetAllTribeTask extends MsTask {

        private int mOffset;

        public GetAllTribeTask(int offset) {
            super(TribeClassifyDetailActivity.this, MsRequest.TRIBE_ALL_TRIBES);
            mOffset = offset;
        }

        @Override
        protected String buildParams() {
            return new StringBuilder("offset=").append(mOffset).toString();
        }

        @Override
        protected void onPostExecute(MsResponse response) {
            mPtrClassifyDetail.onRefreshComplete();
            if (response.isSuccessful()) {
                if (mOffset <= 0) {
                    mTribes.clear();
                }
                ArrayList<TribeInfo> tribes = JsonUtil.getArray(
                        response.getJsonArray(), TribeInfo.TRANSFORMER);
                mTribes.addAll(tribes);
                mAdapter.notifyDataSetChanged();
            }
        }

    }

	@Override
	public void onPullDownToRefresh(PullToRefreshBase<ListView> refreshView) {
		fetchTribes(true);
	}

	@Override
	public void onPullUpToRefresh(PullToRefreshBase<ListView> refreshView) {
		fetchTribes(false);
	}

	private void fetchTribes(boolean refresh) {
		int size = mAdapter.getCount();
		int offset = refresh ? 0 : size;
		if (tribeType == EXT_TYPE_ALL_TRIBE) {
		    new GetAllTribeTask(offset).executeLong();
		} else if (mUserId > 0){
		    new GetConcernTribesTask(mUserId, offset).executeLong();
		} else { 
		    new GetTribeTypeTask(offset).executeLong();
		}
	}

	private void followTribe(TribeInfo tribeInfo) {
		new FollowTribeTask(tribeInfo).executeLong();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.tv_is_concern:
			TribeInfo tribeInfo = (TribeInfo) v.getTag();
			followTribe(tribeInfo);
			break;
		case R.id.rl_tribe_item:
			TribeConcernViewHoder holder = (TribeConcernViewHoder) v.getTag();
			TribeInfo tribe = holder.mTribe;
			Intent itn = new Intent(TribeClassifyDetailActivity.this,
					TribeDetailActivity.class);
			itn.putExtra(TribeInfo.INTENT_EXTRA_INFO, tribe);
			startActivity(itn);
			break;
		default:
			break;
		}
	}

	private class FollowTribeTask extends MsTask {

		private TribeInfo mTribeInfo;

		public FollowTribeTask(TribeInfo tribeInfo) {
			super(TribeClassifyDetailActivity.this,
					!tribeInfo.collected ? MsRequest.TRIBE_FOLLOW_WITH
							: MsRequest.TRIBE_CANCEL_FOLLOW);
			mTribeInfo = tribeInfo;
		}

		@Override
		protected String buildParams() {
			return "tribe_id=" + mTribeInfo.tribeId;
		}

		@Override
		protected void onPostExecute(MsResponse response) {
			if (response.isSuccessful()) {
				mTribeInfo.collected = !mTribeInfo.collected;
				mTribeInfo.followCount = mTribeInfo.followCount + 1;
				mAdapter.notifyDataSetChanged();
			}
		}

	}

	private class GetConcernTribesTask extends MsTask {
        private int userId;
        private int mOffset;

        public GetConcernTribesTask(int uid, int offset) {
            super(TribeClassifyDetailActivity.this, MsRequest.TRIBE_CONCERN_TRIBE);
            userId = uid;
            mOffset = offset;
        }

        @Override
        protected String buildParams() {
            return new StringBuilder("user_id=").append(userId).append("&offset=").append(mOffset).toString();
        }

        @Override
        protected void onPostExecute(MsResponse response) {
            mPtrClassifyDetail.onRefreshComplete();
            if (response.isSuccessful()) {
                if (mOffset <= 0) {
                    mTribes.clear();
                }
                ArrayList<TribeInfo> tribes = JsonUtil.getArray(
                        response.getJsonArray(), TribeInfo.TRANSFORMER);
                mTribes.addAll(tribes);
                mAdapter.notifyDataSetChanged();
            }
        }

    }

}
