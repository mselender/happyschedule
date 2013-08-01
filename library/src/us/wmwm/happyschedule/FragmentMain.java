package us.wmwm.happyschedule;

import us.wmwm.happyschedule.FragmentPickStations.OnGetSchedule;
import us.wmwm.happyschedule.views.FragmentMainAdapter;
import android.app.ActionBar;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class FragmentMain extends Fragment {

	ViewPager pager;
	
	Handler handler = new Handler();
	
	OnBackStackChangedListener onBackStackListener = new OnBackStackChangedListener() {
		@Override
		public void onBackStackChanged() {
			int count = getFragmentManager().getBackStackEntryCount();
			System.out.println("onBackStack " + count);
			if(count==0) {
				handler.post(new Runnable() {
					public void run() {
						ActionBar a = getActivity().getActionBar();
						a.setSubtitle(null);
						a.setDisplayHomeAsUpEnabled(false);
						a.setHomeButtonEnabled(false);
						getActivity().invalidateOptionsMenu();						
					};
				});
				
			}
		}
	};
	
	public void onCreateOptionsMenu(android.view.Menu menu, android.view.MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view =  inflater.inflate(R.layout.fragment_main, container, false);
		pager = (ViewPager) view.findViewById(R.id.pager);
		pager.setPageMargin((int)(getResources().getDimension(R.dimen.activity_horizontal_margin)));
		return view;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getFragmentManager().addOnBackStackChangedListener(onBackStackListener);
		handler.post(new Runnable() {
			@Override
			public void run() {
				final FragmentMainAdapter fma = new FragmentMainAdapter(getFragmentManager());
				pager.setAdapter(fma);
				fma.setOnGetScheduleListener(new OnGetSchedule() {
					
					@Override
					public void onGetSchedule(Station from, Station to) {
						System.out.println(from + " to " + to);
						FragmentTransaction t = getFragmentManager().beginTransaction();
						FragmentSchedule fs = FragmentSchedule.newInstance(from,to);
						t.replace(R.id.fragment_schedule, fs).addToBackStack(null).commit();
						ActionBar a = getActivity().getActionBar();
						a.setDisplayHomeAsUpEnabled(true);
						a.setHomeButtonEnabled(true);
						getActivity().getActionBar().setSubtitle((from.name + " to " + to.name));
					}
				});
//				fma.setOnStationSelectedListener(new OnStationSelectedListener() {
//					
//					@Override
//					public void onStation(Station station) {
//						if(station==null) {
//							pager.setCurrentItem(pager.getCurrentItem()-1);
//							return;
//						}
//						for(int i = 1; i < fma.getCount(); i++) {
//							Station s = fma.getDepartureVision(i);
//							if(s.getId().equals(station.getId())) {
//								pager.setCurrentItem(i);
//								break;
//							}
//						}
//						//pager.invalidate();
//					}
//				});
			}
		});
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		getFragmentManager().removeOnBackStackChangedListener(onBackStackListener);
	}

}
