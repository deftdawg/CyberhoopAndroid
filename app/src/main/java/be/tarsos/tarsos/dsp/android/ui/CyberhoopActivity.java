package be.tarsos.tarsos.dsp.android.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

public class CyberhoopActivity extends ActionBarActivity {
	// ===============================
	// == Cyberhoop for Android App ==
	// ===============================
	// hacked together by Garth Dahlstrom based on the TarsosDSPAndroid codebase
	//
	// This is app is a _very_ crude scoreboard for a Nerf Cyberhoop Basketball Hoop for Android,
	// Nerf Hasbro only ever produced an iOS app, which while very cool, is now broken because
	// they have not updated it not to crash in iOS 9. :(
	//
	// The app works by listening for the "chirps" produced during the shot made sound when the
	// hoop is in "App" mode and the arm sitting inside the hoop is depressed by a ball.
	//
	// It is pretty reliable at recording buckets for the team "A" setting.
	// Team "B" setting works somewhat on an Honor 5X, but not at all on a Nexus 5
	//
	// To install, you'll need to allow unknown sources, get the APK onto your
	// phone (email it to yourself or something) and then install the APK.
	//
	// Please fork and send pull requests.  https://github.com/jumpkick/cyberhoop
	//
	// Credit to the TarsosDSP Android app, this code is based on that and other bits of cut and
	// paste.  GPLV3


	public class Team {
		private double[] detectionFrequencyPitches;
		public int score = 0;
		// Last Sample Time
		public double lastScoreTimeStamp = 0;

		public Team(double[] detectionFrequencyPitches) {
			this.detectionFrequencyPitches = detectionFrequencyPitches;
		}

		public double getMinDetectionFrequencyPitch() {
			return detectionFrequencyPitches[0];
		}
		public double getMaxDetectionFrequencyPitch() {
			return detectionFrequencyPitches[1];
		}

		public boolean notARefractoryPeriod(double timeStamp) {
			// The hoop is not able to produce a "full basket" sound more then once per second.
			// So here we filter the cluster of samples in a single second, so they are collectively counted as 1 basket.
			return timeStamp > lastScoreTimeStamp + 1.0 || timeStamp < lastScoreTimeStamp - 1.0;
		};

	}

	public class myLinkedHashMap<Double, Float> extends LinkedHashMap<java.lang.Double, java.lang.Float> {

		public int existingMatches(double sampleTimeStamp, float pitchInHz) {
			java.lang.Double timeStampDelta = java.lang.Double.valueOf(1.0);
			java.lang.Float pitchDelta = java.lang.Float.valueOf(100);

			if (this.size() <= 1) return 0;
			int matchCount = 0;
			Iterator<Entry<java.lang.Double, java.lang.Float>> sampleEntryIterator = this.entrySet().iterator();
			while (sampleEntryIterator.hasNext()) {
				Map.Entry<java.lang.Double, java.lang.Float> sampleEntry = sampleEntryIterator.next();
				// Log.i("existingMatches", "otsk: " + sampleEntry.getKey());

				if ( java.lang.Double.valueOf(sampleTimeStamp) > sampleEntry.getKey() - timeStampDelta && java.lang.Double.valueOf(sampleTimeStamp) < sampleEntry.getKey() + timeStampDelta) {
					// Log.i("existingMatches", "ts delta: " + sampleTimeStamp + " vs " + sampleEntry.getKey());

					if ( pitchInHz > sampleEntry.getValue() - pitchDelta && pitchInHz < sampleEntry.getValue() + pitchDelta ) {
						matchCount++;
					}
				} else {
					// Too old, discard...
					sampleEntryIterator.remove();
				}
			}
			return matchCount;
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tarsos_dsp);
		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}

		final TreeMap <String, Team> teams = new TreeMap<>();
//		teams.put("A-AVD", new Team(new double[]{2280,2380})); // Android Virtual Device samples at a much lower Hz on the Mac then a real phone
//		teams.put("B-AVD", new Team(new double[]{1310,1410})); // Android Virtual Device samples at a much lower Hz on the Mac then a real phone
		teams.put("A", new Team(new double[]{5700,5800}));
		teams.put("B", new Team(new double[]{6600,6750}));  // FIXME: B is not 100% reliable on Honor 5X, and almost never works on Nexus 5 :(
		teams.put("C", new Team(new double[]{1200,1400})); // Alternate variant try to find a good B value for Nexus 5

		final myLinkedHashMap<Double, Float> sampleQueue = new myLinkedHashMap<Double, Float>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<java.lang.Double, java.lang.Float> eldest)
			{
				return this.size() > 10;
			}
		};

//		AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0); // Basket A reliably detected at 22050 sampling rate! Use this or higher!
		AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100,2048,0);

//		dispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
		dispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 44100,2048, new PitchDetectionHandler() {

			@Override
			public void handlePitch(PitchDetectionResult pitchDetectionResult,
									AudioEvent audioEvent) {
				final float pitchInHz = pitchDetectionResult.getPitch();
				final double sampleTimeStamp = audioEvent.getTimeStamp();
				runOnUiThread(new Runnable() {
				     @Override
				     public void run() {
		   		    	TextView text = (TextView) findViewById(R.id.textView1);
						 text.setTextSize(TypedValue.COMPLEX_UNIT_PX, 220);
						 text.setTextAlignment(text.TEXT_ALIGNMENT_CENTER);

						 TextView debugText = (TextView) findViewById(R.id.textView2);
						 // debugText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 220);
						 debugText.setTextAlignment(text.TEXT_ALIGNMENT_CENTER);

						 for (Map.Entry<String, Team> teamEntry : teams.entrySet()) {
							 if (teamEntry.getValue().notARefractoryPeriod(sampleTimeStamp) && pitchInHz > teamEntry.getValue().getMinDetectionFrequencyPitch() && pitchInHz < teamEntry.getValue().getMaxDetectionFrequencyPitch()) {
								 Log.i("Bucket", "Team " + teamEntry.getKey() + " scores! " + pitchInHz + " @ " + sampleTimeStamp);
								 teamEntry.getValue().score++;
								 teamEntry.getValue().lastScoreTimeStamp = sampleTimeStamp;
								 // Update the scoreboard
								 text.setText("= Score =\n");
								 for (String teamName : teams.keySet()) {
									 if (teams.get(teamName).score > 0) {
										 text.append(teamName + ": " + teams.get(teamName).score + " \n");
									 }
								 }
							 }
						 }

						 if (pitchInHz > -1.0) {
							 int matchCount = sampleQueue.existingMatches(sampleTimeStamp, pitchInHz);
							 Log.i("Sample Info", "Sample Cluster: " + matchCount);
							 if (matchCount > 1) {
								 Log.i("Sample Info", "Sample Cluster Hz @ " + pitchInHz);
								 debugText.setText("Last Sample Hz: " + pitchInHz);
							 }
							 sampleQueue.put(sampleTimeStamp, pitchInHz);
						 }
					 }
				});

			}
		}));
		new Thread(dispatcher,"Audio Dispatcher").start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tarsos_ds, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_tarsos_ds,
					container, false);
			return rootView;
		}
	}
}
