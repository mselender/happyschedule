package us.wmwm.happyschedule.application;

import java.util.Calendar;
import java.util.List;

import us.wmwm.happyschedule.Alarms;
import us.wmwm.happyschedule.R;
import us.wmwm.happyschedule.ThreadHelper;
import us.wmwm.happyschedule.model.Alarm;
import us.wmwm.happyschedule.service.HappyScheduleService;
import us.wmwm.happyschedule.service.Poller;
import android.app.Application;
import android.content.Intent;

import com.amazon.device.ads.AdRegistration;
import com.flurry.android.FlurryAgent;

public class HappyApplication extends Application {

	static HappyApplication INSTANCE;
	
	@Override
	public void onCreate() {
		INSTANCE = this;
		
		super.onCreate();
		FlurryAgent.setReportLocation(false);
		try {
			AdRegistration.setAppKey(getString(R.string.amazon_app_key));
		} catch (Exception e) {
			
		}
		ThreadHelper.getScheduler().submit(new Runnable() {
			@Override
			public void run() {
				List<Alarm> alarms = Alarms.getAlarms(INSTANCE);
				for(Alarm alarm : alarms) {
					if(Calendar.getInstance().before(alarm.getTime())) {
						Alarms.startAlarm(INSTANCE, alarm);
					} else {
						Alarms.removeAlarm(INSTANCE, alarm);
					}
				}
			}
		});
		ThreadHelper.getScheduler().submit(new Runnable() {
			@Override
			public void run() {
				
			}
		});
		startService(new Intent(this,HappyScheduleService.class));
	}
	
	public static HappyApplication get() {
		return INSTANCE;
	}
	
	public static Poller getPoller() {
		String pollerClassName = get().getString(R.string.poller);
		try {
			Class<?> clazz = Class.forName(pollerClassName);
			return (Poller) clazz.newInstance();
		} catch (Exception e) {
			
		}
		return null;
	}
	
}
