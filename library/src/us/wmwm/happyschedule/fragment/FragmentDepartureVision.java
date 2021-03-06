package us.wmwm.happyschedule.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import us.wmwm.happyschedule.R;
import us.wmwm.happyschedule.ThreadHelper;
import us.wmwm.happyschedule.activity.ActivityPickStation;
import us.wmwm.happyschedule.adapter.DepartureVisionAdapter;
import us.wmwm.happyschedule.application.HappyApplication;
import us.wmwm.happyschedule.model.AppConfig;
import us.wmwm.happyschedule.model.LineStyle;
import us.wmwm.happyschedule.model.Station;
import us.wmwm.happyschedule.model.StationToStation;
import us.wmwm.happyschedule.model.TrainStatus;
import us.wmwm.happyschedule.service.Poller;
import us.wmwm.happyschedule.util.Streams;
import us.wmwm.happyschedule.views.OnStationSelectedListener;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.emilsjolander.components.stickylistheaders.StickyListHeadersListView;
import com.flurry.android.FlurryAgent;

public class FragmentDepartureVision extends HappyFragment implements IPrimary,
		ISecondary {

	StickyListHeadersListView list;
	View stationSelect;

	Future<?> poll;

	Future<?> refresh;

	Poller poller;

	DepartureVisionAdapter adapter;

	Handler handler = new Handler();

	ConnectivityManager manager;

	List<TrainStatus> lastStatuses;

	Station station;

	View empty;

	private static final String TAG = FragmentDepartureVision.class
			.getSimpleName();

	StationToStation stationToStation;

	View erroText;

	long lastStatusesReceived;

	AppConfig appConfig;

	OnStationSelectedListener onStationSelected;

	boolean canLoad;

	boolean activityCreated;

	public void setOnStationSelected(OnStationSelectedListener onStationSelected) {
		this.onStationSelected = onStationSelected;
	}

	BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			NetworkInfo info = manager.getActiveNetworkInfo();
			if (info == null || !info.isConnected()) {
				erroText.setVisibility(View.VISIBLE);
			}
			if (!canLoad) {
				return;
			}
			if (info != null && info.isConnected()) {
				erroText.setVisibility(View.GONE);
				if (poll == null || poll.isCancelled()) {
					poll = ThreadHelper.getScheduler().scheduleAtFixedRate(r,
							100, SettingsFragment.getPollMilliseconds(), TimeUnit.MILLISECONDS);
				}
			} else {
				if (poll != null) {
					poll.cancel(true);
				}
			}

		}
	};

	public static FragmentDepartureVision newInstance(Station station,
			StationToStation sts, boolean isOverlay) {
		FragmentDepartureVision dv = new FragmentDepartureVision();
		Bundle b = new Bundle();
		b.putSerializable("station", station);
		if (sts != null) {
			b.putSerializable("stationToStation", sts);
		}
		b.putBoolean("isOverlay", isOverlay);
		dv.setArguments(b);
		return dv;
	}

	private String getKey() {
		return "lastStatuses" + station.getId();
	}

	Runnable r = new Runnable() {
		@Override
		public void run() {
			try {
				final List<TrainStatus> s = poller.getTrainStatuses(appConfig,
						station.getDepartureVision(),null);
				String key = getKey();
				if (s != null && !s.isEmpty()) {
					count++;
					Map<String,String> k = new HashMap<String,String>();
					k.put("station_id", station.getId());
					k.put("station_name", station.getName());
					FlurryAgent.logEvent("DepartureVision",k);
					JSONArray a = new JSONArray();
					if (lastStatuses != null) {
						for (int i = 0; i < lastStatuses.size(); i++) {
							a.put(lastStatuses.get(i).toJSON());
						}
						PreferenceManager
								.getDefaultSharedPreferences(getActivity())
								.edit()
								.putString("lastStation", station.getId())
								.putString(key, a.toString())
								.putString("lastStatuses",
										lastStatuses.toString())
								.putLong(key + "Time",
										System.currentTimeMillis()).commit();
					}

					lastStatuses = s;
				}
				handler.post(new Runnable() {
					public void run() {
						adapter.setData(s);
						if (adapter.getCount() == 0) {
							empty.setVisibility(View.VISIBLE);
						} else {
							empty.setVisibility(View.GONE);
						}
					}
				});

			} catch (Exception e) {
				Log.e(TAG,"error with polling", e);
			}
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_departurevision,
				container, false);
		list = (StickyListHeadersListView) root.findViewById(R.id.list);
		stationSelect = root.findViewById(R.id.departure);
		erroText = root.findViewById(R.id.no_internet);
		empty = root.findViewById(R.id.empty);
		return root;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		JSONArray a = new JSONArray();
		if (lastStatuses != null) {
			for (int i = 0; i < lastStatuses.size(); i++) {
				a.put(lastStatuses.get(i).toJSON());
			}
			String key = "lastStatuses" + station;
			PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
					.putString("lastStation", station.getId())
					.putString(key, a.toString())
					.putLong(key, lastStatusesReceived).commit();
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Bundle args = getArguments();
		boolean showControls = true;
		if (args != null) {
			showControls = !args.containsKey("stationToStation");
		}
		setHasOptionsMenu(showControls);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		FlurryAgent.logEvent(item.getTitle()+"MenuItemSelected");
		if (item.getItemId() == R.id.menu_change_station) {
			startActivityForResult(
					new Intent(ActivityPickStation.from(getActivity(), true)),
					100);
		}
		if (item.getItemId() == R.id.menu_add_station) {
			startActivityForResult(new Intent(getActivity(),
					ActivityPickStation.class), 200);
		}
		if (item.getItemId() == R.id.menu_remove_station) {
			deleteCurrentStation();
		}
		if (item.getItemId() == android.R.id.home) {
			getActivity().onBackPressed();
		}
		if (item.getItemId() == R.id.menu_refresh) {
			// canLoad = true;
			if (refresh != null) {
				refresh.cancel(true);
			}

			refresh = ThreadHelper.getScheduler().submit(r);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (station == null) {
			menu.removeItem(R.id.menu_remove_station);
		} else {
		}
	}

	private void deleteCurrentStation() {
		String k = PreferenceManager.getDefaultSharedPreferences(getActivity())
				.getString("departure_visions", "[]");
		JSONArray a = null;
		try {
			a = new JSONArray(k);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		JSONArray b = new JSONArray();
		for (int i = a.length() - 1; i >= 0; i--) {
			String id = a.optString(i);
			if (station.getId().equals(id)) {
				continue;
			}
			b.put(id);
		}
		PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
				.putString("departure_visions", b.toString()).commit();
		onStationSelected.onStation(null);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		inflater.inflate(R.menu.departurevision, menu);
		Bundle args = getArguments();
		if (!args.getBoolean("isOverlay")) {
			getActivity().getActionBar().setSubtitle("w/ DepartureVision");
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		poller = HappyApplication.getPoller();
		Bundle arguments = getArguments();
		if (arguments != null) {
			station = (Station) arguments.getSerializable("station");
			stationToStation = (StationToStation) arguments
					.getSerializable("stationToStation");
			canLoad = arguments.getBoolean("isOverlay");
		}

		if (stationToStation == null) {
			list.setAdapter(adapter = new DepartureVisionAdapter());
		} else {
			list.setAdapter(adapter = new DepartureVisionAdapter(station,
					stationToStation));
		}

		manager = (ConnectivityManager) getActivity().getSystemService(
				Context.CONNECTIVITY_SERVICE);
		loadColors();
		if (station == null) {
			stationSelect.setVisibility(View.VISIBLE);
			stationSelect.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					startActivityForResult(
							ActivityPickStation.from(getActivity(), true), 100);
				}
			});
			return;
		} else {
			stationSelect.setVisibility(View.GONE);
		}
		loadInitial();
//		FragmentAmazonAd ad = new FragmentAmazonAd();
//		ad.setHappyAdListener(new HappyAdListener() {
//			@Override
//			public void onAd() {
//			}
//
//			@Override
//			public void onAdFailed(int count, boolean noFill) {
//				handler.post(new Runnable() {
//					@Override
//					public void run() {
//						try {
//							FragmentGoogleAd gad = new FragmentGoogleAd();
//							getFragmentManager().beginTransaction()
//									.replace(R.id.fragment_ad, gad).commit();
//						} catch (Exception e) {
//
//						}
//					}
//				});
//			}
//		});
//		getFragmentManager().beginTransaction().replace(R.id.fragment_ad, ad)
//				.commit();
		activityCreated = true;
	}

	private void loadInitial() {
		Long time = PreferenceManager
				.getDefaultSharedPreferences(getActivity()).getLong(
						getKey() + "Time", 0);
		if (System.currentTimeMillis() - time > 50000) {
			return;
		}
		String data = PreferenceManager.getDefaultSharedPreferences(
				getActivity()).getString(getKey(), null);
		if (data == null) {
			return;
		}
		try {
			JSONArray a = new JSONArray(data);
			List<TrainStatus> statuses = new ArrayList<TrainStatus>(a.length());
			for (int i = 0; i < a.length(); i++) {
				statuses.add(new TrainStatus(a.optJSONObject(i)));
			}
			adapter.setData(statuses);

		} catch (Exception e) {

		}
		activityCreated = true;
	}

	private void loadColors() {
		ThreadHelper.getScheduler().submit(new Runnable() {
			@Override
			public void run() {
				Activity a = getActivity();
				if (a == null) {
					return;
				}
				try {
					appConfig = new AppConfig(new JSONObject(Streams
							.readFully(Streams.getStream("config.json"))));
					final Map<String, LineStyle> keyToColor = new HashMap<String, LineStyle>();
					for (LineStyle l : appConfig.getLines()) {
						Iterator<String> keys = l.keys.keySet().iterator();
						while (keys.hasNext()) {
							keyToColor.put(keys.next(), l);
						}
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							adapter.setKeyToColor(keyToColor);
						}
					});
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		getActivity().registerReceiver(
				connectionReceiver,
				new IntentFilter(
						android.net.ConnectivityManager.CONNECTIVITY_ACTION));
		if (station == null) {
			return;
		}
		NetworkInfo i = manager.getActiveNetworkInfo();
		if (i != null && i.isConnected() && canLoad) {
			poll = ThreadHelper.getScheduler().scheduleAtFixedRate(r, 100,
					SettingsFragment.getPollMilliseconds(), TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		getActivity().unregisterReceiver(connectionReceiver);
		if (poll != null) {
			poll.cancel(true);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			Station station = (Station) data.getSerializableExtra("station");
			if (requestCode == 100 || requestCode == 200) {
				String k = PreferenceManager.getDefaultSharedPreferences(
						HappyApplication.get()).getString("departure_visions",
						"[]");
				JSONArray departureVisions = null;
				try {
					departureVisions = new JSONArray(k);
				} catch (Exception e) {
					departureVisions = new JSONArray();
				}
				if (requestCode == 100) {
					if (departureVisions.length() != 0) {
						departureVisions = new JSONArray();
					}
					departureVisions.put(station.getId());
				} else {
					departureVisions.put(station.getId());
				}
				adapter.notifyDataSetInvalidated();
				PreferenceManager
						.getDefaultSharedPreferences(HappyApplication.get())
						.edit()
						.putString("departure_visions",
								departureVisions.toString()).commit();
				this.station = station;
				this.stationSelect.setVisibility(View.GONE);
				if (poll != null) {
					poll.cancel(true);
				}
				if (canLoad && station != null) {
					poll = ThreadHelper.getScheduler().scheduleAtFixedRate(r,
							100, SettingsFragment.getPollMilliseconds(), TimeUnit.MILLISECONDS);
				}
				List<TrainStatus> ks = Collections.emptyList();
				adapter.setData(ks);
			}
			if (onStationSelected != null) {
				onStationSelected.onStation(station);
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	boolean logged = false;
	
	int count;

	@Override
	public void setPrimaryItem() {
		if (activityCreated) {

			canLoad = true;
			NetworkInfo info = manager.getActiveNetworkInfo();
			if (info == null || !info.isConnected()) {
				erroText.setVisibility(View.VISIBLE);
			}
			if (info != null && info.isConnected()) {
				erroText.setVisibility(View.GONE);
			} else {

			}
			if (poll != null) {
				poll.cancel(true);
			}
			if ((poll == null || poll.isCancelled()) && station != null) {
				Log.d(TAG, "Polling!");
				poll = ThreadHelper.getScheduler().scheduleAtFixedRate(r, 100,
						SettingsFragment.getPollMilliseconds(), TimeUnit.MILLISECONDS);
			} else {
				Log.d(TAG, "Not Polling!");
			}
			if(!logged) {
				Map<String,String> k = new HashMap<String,String>();
				if(station!=null) {
					k.put("station_id", station.getId());
					k.put("station_name", station.getName());
				}
				FlurryAgent.logEvent("FragmentDepartureVision",k);
			}
		}

	}

	@Override
	public void setSecondary() {
		canLoad = false;
		if (poll != null) {
			poll.cancel(true);
		}
	}

}
