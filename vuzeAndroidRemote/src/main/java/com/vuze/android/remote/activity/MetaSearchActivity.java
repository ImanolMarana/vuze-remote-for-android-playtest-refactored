/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.vuze.android.remote.activity;

import java.io.Serializable;
import java.util.*;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.FlexibleRecyclerView;
import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.MetaSearchEnginesAdapter;
import com.vuze.android.remote.adapter.MetaSearchEnginesAdapter.MetaSearchEnginesInfo;
import com.vuze.android.remote.adapter.MetaSearchResultsAdapter;
import com.vuze.android.remote.adapter.MetaSearchResultsAdapterFilter;
import com.vuze.android.remote.dialog.DialogFragmentDateRange;
import com.vuze.android.remote.dialog.DialogFragmentSizeRange;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.session.RemoteProfile;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.spanbubbles.DrawableTag;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.android.widget.CustomToast;
import com.vuze.android.widget.PreCachingLayoutManager;
import com.vuze.util.*;

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Search Results from Vuze's MetaSearch
 * <p/>
 * Created by TuxPaper on 4/22/16.
 */
public class MetaSearchActivity
	extends DrawerActivity
	implements TransmissionRPC.MetaSearchResultsListener,
	DialogFragmentSizeRange.SizeRangeDialogListener,
	DialogFragmentDateRange.DateRangeDialogListener, SideListHelper.SideSortAPI
{
	private static final String TAG = "MetaSearch";

	private static final String ID_SORT_FILTER = "-ms";

	@Thunk
	static final int FILTER_INDEX_AGE = 0;

	@Thunk
	static final int FILTER_INDEX_SIZE = 1;

	private static final String DEFAULT_SORT_FIELD = TransmissionVars.FIELD_SEARCHRESULT_RANK;

	private static final boolean DEFAULT_SORT_ASC = false;

	private static final String SAVESTATE_LIST = "list";

	private static final String SAVESTATE_ENGINES = "engines";

	private static final String SAVESTATE_SEARCH_ID = "searchID";

	private static SortByFields[] sortByFields = null;

	@Thunk
	String searchString;

	private RecyclerView lvEngines;

	private RecyclerView lvResults;

	private MetaSearchEnginesAdapter metaSearchEnginesAdapter;

	@Thunk
	MetaSearchResultsAdapter metaSearchResultsAdapter;

	@Thunk
	SideListHelper sideListHelper;

	/**
	 * <HashString, Map of Fields>
	 */
	@Thunk
	final HashMap<String, Map> mapResults = new HashMap<>();

	@Thunk
	HashMap<String, MetaSearchEnginesInfo> mapEngines;

	private TextView tvFilterAgeCurrent;

	private TextView tvFilterSizeCurrent;

	private TextView tvFilterCurrent;

	private TextView tvFilterTop;

	private long maxSize;

	@Thunk
	TextView tvDrawerFilter;

	private List<MetaSearchEnginesInfo> enginesList;

	private SpanTags.SpanTagsListener listenerSpanTags;

	@Thunk
	Serializable searchID;

	@Thunk
	TextView tvHeader;

	@Override
	protected String getTag() {
		return TAG;
	}

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			searchString = intent.getStringExtra(SearchManager.QUERY);
		}

		int SHOW_SIDELIST_MINWIDTH_PX = getResources().getDimensionPixelSize(
				R.dimen.sidelist_search_drawer_until_screen);

		setContentView(AndroidUtils.isTV() ? R.layout.activity_metasearch_tv
				: AndroidUtilsUI.getScreenWidthPx(this) >= SHOW_SIDELIST_MINWIDTH_PX
						? R.layout.activity_metasearch_sb
						: R.layout.activity_metasearch_sb_drawer);
		setupActionBar();

		onCreate_setupDrawer();

		tvFilterTop = (TextView) findViewById(R.id.ms_top_filterarea);
		if (tvFilterTop != null) {

			tvFilterTop.setMovementMethod(LinkMovementMethod.getInstance());

			listenerSpanTags = new SpanTags.SpanTagsListener() {

				@Override
				public void tagClicked(int index, Map mapTag, String name) {
					{
						switch (index) {
							case FILTER_INDEX_AGE:
								ageRow_clicked(null);
								break;
							case FILTER_INDEX_SIZE:
								fileSizeRow_clicked(null);
								break;
						}
					}
				}

				@Override
				public int getTagState(int index, Map mapTag, String name) {
					boolean hasFilter = false;
					switch (index) {
						case FILTER_INDEX_AGE:
							hasFilter = metaSearchResultsAdapter.getFilter().hasPublishTimeFilter();
							break;
						case FILTER_INDEX_SIZE:
							hasFilter = metaSearchResultsAdapter.getFilter().hasSizeFilter();
							break;
					}

					return hasFilter ? SpanTags.TAG_STATE_SELECTED
							: SpanTags.TAG_STATE_UNSELECTED;
				}
			};
		}
		tvDrawerFilter = (TextView) findViewById(R.id.sidelist_topinfo);
		tvHeader = (TextView) findViewById(R.id.ms_header);

		MetaSearchResultsAdapter.MetaSearchSelectionListener metaSearchSelectionListener = new MetaSearchResultsAdapter.MetaSearchSelectionListener() {
			@Override
			public void onItemClick(MetaSearchResultsAdapter adapter, int position) {
				if (!AndroidUtils.usesNavigationControl()) {
					// touch users have their own download button
					// nav pad people have click to download, hold-click for menu
					return;
				}

				String id = adapter.getItem(position);
				downloadResult(id);
			}

			@Override
			public void newButtonClicked(String id, boolean currentlyNew) {
			}

			@Override
			public boolean onItemLongClick(MetaSearchResultsAdapter adapter,
					int position) {
				return false;
			}

			@Override
			public void onItemSelected(MetaSearchResultsAdapter adapter, int position,
					boolean isChecked) {

			}

			@Override
			public void onItemCheckedChanged(MetaSearchResultsAdapter adapter,
					String item, boolean isChecked) {

			}

			@Override
			public Map getSearchResultMap(String id) {
				return mapResults.get(id);
			}

			@Override
			public List<String> getSearchResultList() {
				return new ArrayList<>(mapResults.keySet());
			}

			@Override
			public MetaSearchEnginesInfo getSearchEngineMap(String engineID) {
				return mapEngines.get(engineID);
			}

			@Override
			public void downloadResult(String id) {
				Map map = getSearchResultMap(id);
				if (map == null) {
					return;
				}

				Resources resources = getResources();

				final String name = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_NAME, "torrent");

				String engineID = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);

				MetaSearchEnginesInfo engineInfo = mapEngines.get(engineID);
				String engineName = engineInfo == null ? "default" : engineInfo.name;

				final List<String> listNames = new ArrayList<>();
				final List<String> listURLs = new ArrayList<>();
				boolean gotHash = false;

				String url = MapUtils.getMapString(map,
						TransmissionVars.FIELD_SEARCHRESULT_URL, null);
				if (url != null && url.length() > 0) {
					String s;
					if (url.startsWith("magnet:")) {
						s = resources.getString(R.string.download_source_item_from_hash);
						gotHash = true;
					} else {
						s = resources.getString(R.string.download_source_item_from_url,
								engineName);
					}
					listNames.add(s);
					listURLs.add(url);
				}

				if (!gotHash) {
					String hash = MapUtils.getMapString(map,
							TransmissionVars.FIELD_SEARCHRESULT_HASH, null);

					if (hash != null && hash.length() > 0) {
						String s = resources.getString(
								R.string.download_source_item_from_hash);
						listNames.add(s);
						listURLs.add(hash);
						gotHash = true;
					}
				}

				List others = MapUtils.getMapList(map, "others", null);

				if (others != null && others.size() > 0) {
					for (Object other : others) {
						if (other instanceof Map) {
							map = (Map) other;
							engineID = MapUtils.getMapString(map,
									TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID, null);

							engineInfo = mapEngines.get(engineID);
							engineName = engineInfo == null ? "default" : engineInfo.name;

							url = MapUtils.getMapString(map,
									TransmissionVars.FIELD_SEARCHRESULT_URL, null);
							if (url != null && url.length() > 0) {
								String s = resources.getString(
										R.string.download_source_item_from_url, engineName);
								listNames.add(s);
								listURLs.add(url);
							}

							if (!gotHash) {
								String hash = MapUtils.getMapString(map,
										TransmissionVars.FIELD_SEARCHRESULT_HASH, null);

								if (hash != null && hash.length() > 0) {
									String s = resources.getString(
											R.string.download_source_item_from_hash);
									listNames.add(s);
									listURLs.add(hash);
									gotHash = true;
								}
							}

						}
					}

				}

				if (listNames.size() == 0) {
					CustomToast.showText("Error getting Search Result URL",
							Toast.LENGTH_SHORT);
				} else if (listNames.size() > 1) {
					String[] items = listNames.toArray(new String[listNames.size()]);

					AlertDialog.Builder build = new AlertDialog.Builder(
							MetaSearchActivity.this);
					build.setTitle(R.string.select_download_source);
					build.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (which >= 0 && which < listURLs.size()) {
								String url = listURLs.get(which);
								session.torrent.openTorrent(MetaSearchActivity.this, url, name);
							}

						}
					});

					build.show();
				} else {
					session.torrent.openTorrent(MetaSearchActivity.this, listURLs.get(0),
							name);
				}
			}

		};
		metaSearchResultsAdapter = new MetaSearchResultsAdapter(this,
				metaSearchSelectionListener, R.layout.row_ms_result,
				R.layout.row_ms_result_dpad) {
			@Override
			public void lettersUpdated(HashMap<String, Integer> mapLetters) {
				sideListHelper.lettersUpdated(mapLetters);
			}
		};
		metaSearchResultsAdapter.registerAdapterDataObserver(
				new RecyclerView.AdapterDataObserver() {
					@Override
					public void onChanged() {
						updateHeader();
					}

					@Override
					public void onItemRangeInserted(int positionStart, int itemCount) {
						updateHeader();
					}

					@Override
					public void onItemRangeRemoved(int positionStart, int itemCount) {
						updateHeader();
					}
				});
		metaSearchResultsAdapter.setMultiCheckModeAllowed(false);
		metaSearchResultsAdapter.setCheckOnSelectedAfterMS(50);
		lvResults = (RecyclerView) findViewById(R.id.ms_list_results);
		lvResults.setAdapter(metaSearchResultsAdapter);
		lvResults.setLayoutManager(new PreCachingLayoutManager(this));

		if (AndroidUtils.isTV()) {
			((FastScrollRecyclerView) lvResults).setEnableFastScrolling(false);
			((FlexibleRecyclerView) lvResults).setFixedVerticalHeight(
					AndroidUtilsUI.dpToPx(48));
			lvResults.setVerticalFadingEdgeEnabled(true);
			lvResults.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		setupSideListArea(this.getWindow().getDecorView());

		RemoteProfile remoteProfile = session.getRemoteProfile();
		String[] sortBy = remoteProfile.getSortBy(ID_SORT_FILTER,
				DEFAULT_SORT_FIELD);
		Boolean[] sortOrder = remoteProfile.getSortOrderAsc(ID_SORT_FILTER,
				DEFAULT_SORT_ASC);
		if (sortBy != null) {
			int which = TorrentUtils.findSordIdFromTorrentFields(sortBy,
					getSortByFields(this));
			sortBy(sortBy, sortOrder, which, false);
		}

		if (savedInstanceState != null) {
			HashMap savedEngines = (HashMap) savedInstanceState.getSerializable(
					SAVESTATE_ENGINES);
			String list = savedInstanceState.getString(SAVESTATE_LIST);
			searchID = savedInstanceState.getSerializable(SAVESTATE_SEARCH_ID);
			if (list != null && savedEngines != null) {
				Map<String, Object> map = JSONUtils.decodeJSONnoException(list);

				if (map != null) {
					for (String key : map.keySet()) {
						Object o = map.get(key);
						if (o instanceof Map) {
							mapResults.put(key, (Map) o);
						}
					}
				}

				mapEngines = savedEngines;

				updateEngineList();

				// hackkkkk.. should call a function like TransmissionRPC.continueMetaSearch(searchID, listener)
				final Map<String, Object> mapResultsRequest = new HashMap<>();
				mapResultsRequest.put("sid", searchID);
				session.executeRpc(new Session.RpcExecuter() {
					@Override
					public void executeRpc(final TransmissionRPC rpc) {
						rpc.simpleRpcCall(TransmissionVars.METHOD_VUZE_SEARCH_GET_RESULTS,
								mapResultsRequest, new ReplyMapReceivedListener() {

									@Override
									public void rpcSuccess(String id, Map<?, ?> optionalMap) {

										boolean complete = MapUtils.getMapBoolean(optionalMap,
												"complete", true);
										if (!complete) {
											try {
												Thread.sleep(1500);
											} catch (InterruptedException ignored) {
											}
											rpc.simpleRpcCall(
													TransmissionVars.METHOD_VUZE_SEARCH_GET_RESULTS,
													mapResultsRequest, this);
										}

										List listEngines = MapUtils.getMapList(optionalMap,
												SAVESTATE_ENGINES, Collections.emptyList());

										onMetaSearchGotResults(searchID, listEngines, complete);
									}

									@Override
									public void rpcFailure(String id, String message) {
									}

									@Override
									public void rpcError(String id, Exception e) {
									}
								});
					}

				});
			}
			// What if the search was not done?
		}

		if (mapResults.size() == 0) {
			doMySearch();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (metaSearchResultsAdapter != null) {
			metaSearchResultsAdapter.onSaveInstanceState(outState);
		}
		if (sideListHelper != null) {
			sideListHelper.onSaveInstanceState(outState);
		}
		outState.putString(SAVESTATE_LIST, JSONUtils.encodeToJSON(mapResults));
		outState.putSerializable(SAVESTATE_ENGINES, mapEngines);
		outState.putSerializable(SAVESTATE_SEARCH_ID, searchID);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (metaSearchResultsAdapter != null) {
			metaSearchResultsAdapter.onRestoreInstanceState(savedInstanceState,
					lvResults);
		}
		if (sideListHelper != null) {
			sideListHelper.onRestoreInstanceState(savedInstanceState);
		}
		updateFilterTexts();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (sideListHelper != null) {
			sideListHelper.onResume();
		}
	}

	private void doMySearch() {
		session.executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.startMetaSearch(searchString, MetaSearchActivity.this);
			}
		});
	}

	private void setupActionBar() {
		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
		if (toolBar != null) {
			if (AndroidUtils.isTV()) {
				toolBar.setVisibility(View.GONE);
				return;
			}
			setSupportActionBar(toolBar);
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			System.err.println("actionBar is null");
			return;
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setTitle(remoteProfile.getNick());
		actionBar.setSubtitle(searchString);

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	@Override
	public void onDrawerOpened(View view) {
		setupSideListArea(view);
		updateFilterTexts();
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onMetaSearchGotResults(Serializable searchID, List engines,
                                        final boolean complete) {
    if (isFinishing()) {
      return false;
    }
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        handleProgressBars(complete);
      }
    });

    for (Object oEngine : engines) {
      if (!(oEngine instanceof Map)) {
        continue;
      }
      processEngineResult((Map) oEngine);
    }

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        metaSearchResultsAdapter.getFilter().refilter();
      }
    });
    return true;
  }
  
  private void handleProgressBars(boolean complete){
    ProgressBar progressBar = (ProgressBar) findViewById(
            R.id.progress_spinner);
    if (progressBar != null) {
      progressBar.setVisibility(complete ? View.GONE : View.VISIBLE);
    }
    ProgressBar enginesPB = (ProgressBar) findViewById(
            R.id.metasearch_engines_spinner);
    if (enginesPB != null) {
      enginesPB.setVisibility(complete ? View.GONE : View.VISIBLE);
    }
  }
  
  private void processEngineResult(Map mapEngine){
    List listResults = MapUtils.getMapList(mapEngine, "results", null);

    int count = (listResults == null) ? 0 : listResults.size();
    String engineID = MapUtils.getMapString(mapEngine, "id", null);
    if (metaSearchEnginesAdapter != null) {
      String error = MapUtils.getMapString(mapEngine, "error", null);
      metaSearchEnginesAdapter.refreshItem(engineID,
              MapUtils.getMapBoolean(mapEngine, "complete", false),
              error == null ? count : -1);
    }

    if (listResults != null) {
      for (Object oResult : listResults) {
        if (!(oResult instanceof Map)) {
          if (AndroidUtils.DEBUG) {
            Log.d(TAG, "onMetaSearchGotResults: NOT A MAP: " + oResult);
          }
          continue;
        }

        processResult(engineID, (Map) oResult);
      }
    }
  }
  
  private void processResult(String engineID, Map oResult){
    Map<String, Object> mapResult = fixupResultMap((Map) oResult);

    long size = MapUtils.getMapLong(mapResult,
            TransmissionVars.FIELD_SEARCHRESULT_SIZE, 0);
    if (size > maxSize) {
      maxSize = size;
    }

    String hash = MapUtils.getMapString(mapResult,
            TransmissionVars.FIELD_SEARCHRESULT_HASH, null);
    if (hash == null) {
      hash = MapUtils.getMapString(mapResult,
              TransmissionVars.FIELD_SEARCHRESULT_URL, null);
    }
    if (hash != null) {
      storeResult(engineID, hash, mapResult);
    } else {
      if (AndroidUtils.DEBUG) {
        Log.d(TAG, "onMetaSearchGotResults: No hash for " + mapResult);
      }
    }
  }
  
  private void storeResult(String engineID, String hash, Map<String, Object> mapResult){
    mapResult.put(TransmissionVars.FIELD_SEARCHRESULT_ENGINE_ID,
            engineID);
    Map mapExisting = mapResults.get(hash);
    if (mapExisting != null) {
      List others = MapUtils.getMapList(mapExisting, "others", null);
      if (others == null) {
        others = new ArrayList();
        mapExisting.put("others", others);
      }
      others.add(mapResult);
    } else {
      mapResults.put(hash, mapResult);
    }
  }

//Refactoring end
	}

	/**
	 * Unfortunately, the search results map returns just about everything in
	 * Strings, including numbers.
	 */
	private static Map<String, Object> fixupResultMap(
			Map<String, Object> mapResult) {
		final String[] IDS_LONG = {
			TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE,
			TransmissionVars.FIELD_SEARCHRESULT_PEERS,
			TransmissionVars.FIELD_SEARCHRESULT_SIZE,
			TransmissionVars.FIELD_SEARCHRESULT_SEEDS,
		};
		final String[] IDS_FLOAT = {
			TransmissionVars.FIELD_SEARCHRESULT_RANK,
		};

		for (String id : IDS_LONG) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Long l = Long.valueOf((String) o);
					mapResult.put(id, l);
				} catch (Throwable ignore) {
				}
			}
		}

		for (String id : IDS_FLOAT) {
			Object o = mapResult.get(id);
			if (o instanceof String) {
				try {
					Double d = Double.valueOf((String) o);
					mapResult.put(id, d);
				} catch (Throwable ignore) {
				}
			}
		}

		return mapResult;
	}

	@Thunk
	void updateHeader() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (isFinishing()) {
					return;
				}

				ActionBar ab = getSupportActionBar();

				int filteredCount = metaSearchResultsAdapter.getItemCount();
				int count = mapResults.size();
				String countString = DisplayFormatters.formatNumber(count);

				String sResultsCount;
				if (count == filteredCount) {
					sResultsCount = getResources().getQuantityString(
							R.plurals.ms_results_header, count, countString, searchString);
				} else {
					sResultsCount = getResources().getQuantityString(
							R.plurals.ms_filtered_results_header, count,
							DisplayFormatters.formatNumber(filteredCount), countString,
							searchString);
				}

				Spanned span = AndroidUtils.fromHTML(sResultsCount);

				if (tvDrawerFilter != null) {
					tvDrawerFilter.setText(span);
				}
				if (tvHeader != null) {
					tvHeader.setText(span);
				}

				if (ab != null) {
					ab.setSubtitle(span);
				}
			}
		});
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_PROG_GREEN) {
			lvEngines.requestFocus();
		} else if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
			Log.d(TAG, "onKeyUp: Engine: " + getCurrentFocus());
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onMetaSearchGotEngines(Serializable searchID, List engines) {
		if (isFinishing()) {
			return false;
		}
		this.searchID = searchID;
		mapEngines = new HashMap<>();

		mapEngines.put("", new MetaSearchEnginesInfo("", "All", null, true));

		for (Object oEngine : engines) {
			if (!(oEngine instanceof Map)) {
				continue;
			}
			Map mapEngine = (Map) oEngine;
			String name = MapUtils.getMapString(mapEngine, "name", null);
			if (name != null) {
				String uid = MapUtils.getMapString(mapEngine, "id", name);
				String favicon = MapUtils.getMapString(mapEngine,
						TransmissionVars.FIELD_SUBSCRIPTION_FAVICON, name);
				MetaSearchEnginesInfo item = new MetaSearchEnginesInfo(uid, name,
						favicon, false);
				mapEngines.put(uid, item);
			}
		}

		updateEngineList();
		return true;
	}

	private void updateEngineList() {
		Collection<MetaSearchEnginesInfo> itemsCollection = mapEngines.values();
		MetaSearchEnginesInfo[] items = itemsCollection.toArray(
				new MetaSearchEnginesInfo[itemsCollection.size()]);
		List<MetaSearchEnginesInfo> list = Arrays.asList(items);
		Arrays.sort(items, new Comparator<MetaSearchEnginesInfo>() {
			@Override
			public int compare(MetaSearchEnginesInfo lhs, MetaSearchEnginesInfo rhs) {
				if (lhs.uid.length() == 0) {
					return -1;
				}
				if (rhs.uid.length() == 0) {
					return 1;
				}
				return lhs.name.compareTo(rhs.name);
			}
		});

		enginesList = list;

		if (metaSearchEnginesAdapter != null) {
			metaSearchEnginesAdapter.setItems(enginesList);
		}
	}

	private void setupSideListArea(View view) {
		if (sideListHelper == null || !sideListHelper.isValid()) {
			sideListHelper = new SideListHelper(this, view, R.id.sidelist_layout, 0,
					0, 0, 0, 500);
			if (!sideListHelper.isValid()) {
				return;
			}

			sideListHelper.addEntry(view, R.id.sidesort_header, R.id.sidesort_list);
			sideListHelper.addEntry(view, R.id.sidefilter_header,
					R.id.sidefilter_list);
			sideListHelper.addEntry(view, R.id.sidetextfilter_header,
					R.id.sidetextfilter_list);
			sideListHelper.addEntry(view, R.id.sideengine_header,
					R.id.sideengine_list);
		}

		View sideListArea = view.findViewById(R.id.sidelist_layout);

		if (sideListArea != null && sideListArea.getVisibility() == View.VISIBLE) {
			sideListHelper.setupSideTextFilter(view, R.id.sidetextfilter_list,
					R.id.sidefilter_text, lvResults,
					metaSearchResultsAdapter.getFilter());
			sideListHelper.setupSideSort(view, R.id.sidesort_list,
					R.id.ms_sort_current, this);

			setupSideFilters(view);
		}

		if (setupSideEngines(R.id.sideengine_list)) {
			lvEngines.setLayoutManager(new PreCachingLayoutManager(this));
		}

		if (sideListHelper.hasSideTextFilterArea()) {
			metaSearchResultsAdapter.getFilter().setBuildLetters(true);
		}
	}

	private boolean setupSideEngines(int id) {
		if (lvEngines != null) {
			return false;
		}
		lvEngines = (RecyclerView) findViewById(id);

		if (lvEngines == null) {
			return false;
		}

		metaSearchEnginesAdapter = new MetaSearchEnginesAdapter(this,
				new FlexibleRecyclerSelectionListener<MetaSearchEnginesAdapter, MetaSearchEnginesInfo>() {
					@Override
					public void onItemClick(MetaSearchEnginesAdapter adapter,
							int position) {

					}

					@Override
					public boolean onItemLongClick(MetaSearchEnginesAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(MetaSearchEnginesAdapter adapter,
							int position, boolean isChecked) {

					}

					@Override
					public void onItemCheckedChanged(MetaSearchEnginesAdapter adapter,
							MetaSearchEnginesInfo item, boolean isChecked) {

						if (isChecked) {
							if (item.uid.length() == 0) {
								if (adapter.getCheckedItemCount() > 1) {
									adapter.clearChecked();
									adapter.setItemChecked(item, true);
									return;
								}
							} else if (adapter.isItemChecked(0)) {
								adapter.setItemChecked(0, false);
								return;
							}
						}

						MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();

						List<MetaSearchEnginesInfo> checkedItems = adapter.getCheckedItems();

						List<String> engineIDs = new ArrayList<>();
						for (MetaSearchEnginesInfo info : checkedItems) {
							engineIDs.add(info.uid);
						}

						filter.setEngines(engineIDs);
						filter.refilter();
					}
				});

		metaSearchEnginesAdapter.setMultiCheckModeAllowed(true);
		metaSearchEnginesAdapter.setMultiCheckMode(true);
		metaSearchEnginesAdapter.setAlwaysMultiSelectMode(true);
		metaSearchEnginesAdapter.setCheckOnSelectedAfterMS(
				FlexibleRecyclerAdapter.NO_CHECK_ON_SELECTED);
		lvEngines.setAdapter(metaSearchEnginesAdapter);

		if (enginesList != null) {
			metaSearchEnginesAdapter.setItems(enginesList);
		}

		return true;
	}

	public SortByFields[] getSortByFields(Context context) {
		if (sortByFields != null) {
			return sortByFields;
		}
		String[] sortNames = context.getResources().getStringArray(
				R.array.sortby_ms_list);

		sortByFields = new SortByFields[sortNames.length];
		int i = 0;

		//<item>Rank</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_RANK
		}, new Boolean[] {
			false
		});

		i++; // <item>Name</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_NAME
		}, new Boolean[] {
			true
		}, true);

		i++; // <item>Seeds</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_SEEDS,
			TransmissionVars.FIELD_SEARCHRESULT_PEERS
		}, new Boolean[] {
			false,
			false
		});

		i++; // <item>size</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_SIZE
		}, new Boolean[] {
			false
		});

		i++; // <item>PublishDate</item>
		sortByFields[i] = new SortByFields(i, sortNames[i], new String[] {
			TransmissionVars.FIELD_SEARCHRESULT_PUBLISHDATE
		}, new Boolean[] {
			false
		});

		return sortByFields;
	}

	private int findSordIdFromSearchResultFields(Context context,
			String[] fields) {
		SortByFields[] sortByFields = getSortByFields(context);

		for (int i = 0; i < sortByFields.length; i++) {
			if (Arrays.equals(sortByFields[i].sortFieldIDs, fields)) {
				return i;
			}
		}
		return -1;
	}

	public void sortBy(final String[] sortFieldIDs, final Boolean[] sortOrderAsc,
			final int which, boolean save) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "SORT BY " + Arrays.toString(sortFieldIDs));
		}
		if (metaSearchResultsAdapter != null) {
			metaSearchResultsAdapter.setSort(sortFieldIDs, sortOrderAsc);
		}
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				sideListHelper.setCurrentSort(MetaSearchActivity.this, which,
						sortOrderAsc[0]);
			}
		});

		if (save) {
			session.getRemoteProfile().setSortBy(ID_SORT_FILTER, sortFieldIDs,
					sortOrderAsc);
			session.saveProfile();
		}
	}

	public void flipSortOrder() {
		RemoteProfile remoteProfile = session.getRemoteProfile();
		Boolean[] sortOrder = remoteProfile.getSortOrderAsc(ID_SORT_FILTER, false);
		if (sortOrder == null) {
			return;
		}
		for (int i = 0; i < sortOrder.length; i++) {
			sortOrder[i] = !sortOrder[i];
		}
		String[] sortBy = remoteProfile.getSortBy(ID_SORT_FILTER,
				DEFAULT_SORT_FIELD);
		sortBy(sortBy, sortOrder, findSordIdFromSearchResultFields(this, sortBy),
				true);
	}

	private void setupSideFilters(View view) {
		tvFilterAgeCurrent = (TextView) view.findViewById(
				R.id.ms_filter_age_current);
		tvFilterSizeCurrent = (TextView) view.findViewById(
				R.id.ms_filter_size_current);
		tvFilterCurrent = (TextView) view.findViewById(R.id.ms_filter_current);

		updateFilterTexts();
	}

	@Thunk
	void updateFilterTexts() {
		if (!AndroidUtilsUI.isUIThread()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateFilterTexts();
				}
			});
			return;
		}

		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();

		long[] timeRange = filter.getFilterTimes();
		String sCombined = "";

		Resources resources = getResources();

		String filterTimeText;
		String filterSizeText;

		if (timeRange[0] <= 0 && timeRange[1] <= 0) {
			filterTimeText = resources.getString(R.string.filter_time_none);
		} else {
			if (timeRange[1] > 0 && timeRange[0] > 0) {
				filterTimeText = DateUtils.formatDateRange(this, timeRange[0],
						timeRange[1],
						DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
			} else if (timeRange[0] > 0) {
				filterTimeText = resources.getString(R.string.filter_date_starting,
						DateUtils.getRelativeTimeSpanString(this, timeRange[0], true));
			} else {
				filterTimeText = resources.getString(R.string.filter_date_until,
						DateUtils.getRelativeTimeSpanString(timeRange[1],
								System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS));
			}
			sCombined += filterTimeText;
		}

		if (tvFilterAgeCurrent != null) {
			tvFilterAgeCurrent.setText(filterTimeText);
		}

		long[] sizeRange = filter.getFilterSizes();
		if (sizeRange[0] <= 0 && sizeRange[1] <= 0) {
			filterSizeText = resources.getString(R.string.filter_size_none);
		} else {
			if (sizeRange[0] > 0 && sizeRange[1] > 0) {
				filterSizeText = resources.getString(R.string.filter_size,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true),
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else if (sizeRange[1] > 0) {
				filterSizeText = resources.getString(R.string.filter_size_upto,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else {
				filterSizeText = resources.getString(R.string.filter_size_atleast,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true));
			}
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterSizeText;
		}

		if (tvFilterSizeCurrent != null) {
			tvFilterSizeCurrent.setText(filterSizeText);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

		if (tvFilterTop != null) {
			SpanTags spanTag = new SpanTags(this, session, tvFilterTop,
					listenerSpanTags);
			spanTag.setLinkTags(false);
			spanTag.setShowIcon(false);
			spanTag.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
			List<Map<?, ?>> listFilters = new ArrayList<>();
			listFilters.add(makeFilterListMap(FILTER_INDEX_AGE, filterTimeText,
					filter.hasPublishTimeFilter()));
			listFilters.add(makeFilterListMap(FILTER_INDEX_SIZE, filterSizeText,
					filter.hasSizeFilter()));
			spanTag.setTagMaps(listFilters);
			spanTag.updateTags();
		}

		updateHeader();
	}

	private static HashMap<Object, Object> makeFilterListMap(long uid,
			String name, boolean enabled) {
		HashMap<Object, Object> map = new HashMap<>();
		map.put(TransmissionVars.FIELD_TAG_UID, uid);
		map.put(TransmissionVars.FIELD_TAG_NAME, name);
		map.put(DrawableTag.KEY_ROUNDED, true);
		map.put(TransmissionVars.FIELD_TAG_COLOR,
				enabled ? 0xFF000000 : 0x80000000);
		map.put(DrawableTag.KEY_FILL_COLOR, enabled ? 0xFF80ffff : 0x4080ffff);
		return map;
	}

	@SuppressWarnings("UnusedParameters")
	public void fileSizeRow_clicked(@Nullable View view) {
		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		long[] sizeRange = filter.getFilterSizes();

		DialogFragmentSizeRange.openDialog(getSupportFragmentManager(), null,
				remoteProfileID, maxSize, sizeRange[0], sizeRange[1]);
	}

	@SuppressWarnings("UnusedParameters")
	public void ageRow_clicked(@Nullable View view) {
		if (metaSearchResultsAdapter == null) {
			return;
		}

		MetaSearchResultsAdapterFilter filter = metaSearchResultsAdapter.getFilter();
		long[] timeRange = filter.getFilterTimes();

		DialogFragmentDateRange.openDialog(getSupportFragmentManager(), null,
				remoteProfileID, timeRange[0], timeRange[1]);
	}

	@Override
	public void onSizeRangeChanged(String callbackID, long start, long end) {
		if (metaSearchResultsAdapter == null) {
			return;
		}
		metaSearchResultsAdapter.getFilter().setFilterSizes(start, end);
		metaSearchResultsAdapter.getFilter().refilter();
		updateFilterTexts();
	}

	@Override
	public void onDateRangeChanged(String callbackID, long start, long end) {
		if (metaSearchResultsAdapter == null) {
			return;
		}
		metaSearchResultsAdapter.getFilter().setFilterTimes(start, end);
		metaSearchResultsAdapter.getFilter().refilter();
		updateFilterTexts();
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

}
