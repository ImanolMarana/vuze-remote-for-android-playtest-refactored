/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote.adapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivityTV;
import com.vuze.android.remote.adapter.TorrentListAdapter.ViewHolderFlipValidator;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.spanbubbles.SpanBubbles;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.android.util.TextViewFlipper;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.view.View;

/**
 * Fills one Torrent info row.
 * <p/>
 * Split out from {@link TorrentListAdapter} so that
 * {@link TorrentDetailsActivityTV} can use it for its top area
 */
public class TorrentListRowFiller
{
	@SuppressWarnings("unused")
	private static final String TAG = "TL_RowFiller";

	private final int colorBGTagState;

	private final int colorFGTagState;

	private final TextViewFlipper flipper;

	private final Context context;

	private TorrentListViewHolder viewHolder;

	public TorrentListRowFiller(Context context, View parentView) {
		this(context);
		this.viewHolder = new TorrentListViewHolder(null, parentView, false);
	}

	protected TorrentListRowFiller(Context context) {
		this.context = context;
		colorBGTagState = AndroidUtilsUI.getStyleColor(context,
				R.attr.bg_tag_type_2);
		colorFGTagState = AndroidUtilsUI.getStyleColor(context,
				R.attr.fg_tag_type_2);

		flipper = TextViewFlipper.create();
	}

	public void fillHolder(Map<?, ?> item, Session session) {
		fillHolder(viewHolder, item, session);
	}

	protected void fillHolder(TorrentListViewHolder holder, Map<?, ?> item,
			Session session) {
		long torrentID = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_ID, -1);

		Resources resources = holder.tvName.getResources();

		holder.animateFlip = holder.torrentID == torrentID;
		holder.torrentID = torrentID;
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID);

		if (holder.ivChecked != null) {
			holder.ivChecked.setVisibility(
					AndroidUtils.hasTouchScreen() ? View.GONE : View.VISIBLE);
		}

		String torrentName = MapUtils.getMapString(item,
				TransmissionVars.FIELD_TORRENT_NAME, " ");
		if (holder.tvName != null) {
			flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(torrentName),
					holder.animateFlip, validator);
		}

		fillProgressInfo(holder, item, validator);

		fillTorrentInfo(holder, item, resources, validator);

		fillETAInfo(holder, item, resources, validator);

		fillRateInfo(holder, item, validator);

		fillStatusInfo(holder, item, session, validator);

		fillTagsInfo(holder, item, session, validator);
	}
	
	private void fillProgressInfo(TorrentListViewHolder holder, Map<?, ?> item, ViewHolderFlipValidator validator) {
		int fileCount = MapUtils.getMapInt(item,
				TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
		long size = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE, 0);
		boolean isMagnetDownload = MapUtils.getMapString(item,
				TransmissionVars.FIELD_TORRENT_NAME, " ").startsWith("Magnet download for ")
				&& fileCount == 0;

		long errorStat = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_ERROR, TransmissionVars.TR_STAT_OK);

		float pctDone = MapUtils.getMapFloat(item,
				TransmissionVars.FIELD_TORRENT_PERCENT_DONE, -1f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = pctDone < 0 || isMagnetDownload
					|| (!holder.isSmall && pctDone >= 1) ? "" : format.format(pctDone);
			flipper.changeText(holder.tvProgress, s, holder.animateFlip, validator);
		}
		if (holder.pb != null) {
			if (isMagnetDownload && errorStat == 3) {
				holder.pb.setVisibility(View.INVISIBLE);
			} else {
				holder.pb.setVisibility(View.VISIBLE);
				boolean shouldBeIndeterminate = pctDone < 0 || isMagnetDownload;
				if (shouldBeIndeterminate != holder.pb.isIndeterminate()) {
					holder.pb.setIndeterminate(shouldBeIndeterminate);
				}
				if (!shouldBeIndeterminate
						&& holder.pb.getProgress() != (int) (pctDone * 10000)) {
					holder.pb.setProgress((int) (pctDone * 10000));
				}
			}
		}
	}
	
	private void fillTorrentInfo(TorrentListViewHolder holder, Map<?, ?> item, Resources resources, ViewHolderFlipValidator validator) {
		int fileCount = MapUtils.getMapInt(item,
				TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
		long size = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE, 0);
		boolean isMagnetDownload = MapUtils.getMapString(item,
				TransmissionVars.FIELD_TORRENT_NAME, " ").startsWith("Magnet download for ")
				&& fileCount == 0;
		
		if (holder.tvInfo != null) {
			String s;

			if (isMagnetDownload) {
				s = "";
			} else if (fileCount == 1) {
				s = DisplayFormatters.formatByteCountToKiBEtc(size);
			} else {
				s = resources.getQuantityString(R.plurals.torrent_row_info, fileCount,
						fileCount)
						+ resources.getString(R.string.torrent_row_info2,
						DisplayFormatters.formatByteCountToKiBEtc(size));
			}
			long error = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_ERROR, TransmissionVars.TR_STAT_OK);
			if (error != TransmissionVars.TR_STAT_OK) {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item,
						TransmissionVars.FIELD_TORRENT_ERROR_STRING, "");
				if (holder.tvTrackerError != null) {
					flipper.changeText(holder.tvTrackerError, errorString,
							holder.animateFlip, validator);
				} else {
					if (s.length() > 0) {
						s += holder.isSmall
								? resources.getString(R.string.torrent_row_line_split) : "\n";
					}
					s += "<color=\"#800\">" + errorString + "</color>";
				}
			} else if (holder.tvTrackerError != null) {
				flipper.changeText(holder.tvTrackerError, "", holder.animateFlip,
						validator);
			}

			flipper.changeText(holder.tvInfo, AndroidUtils.fromHTML(s),
					holder.animateFlip, validator);
		}
	}
	
	private void fillETAInfo(TorrentListViewHolder holder, Map<?, ?> item, Resources resources, ViewHolderFlipValidator validator) {
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_ETA, -1);
			String s = "";
			if (etaSecs > 0 && etaSecs * 1000L < DateUtils.WEEK_IN_MILLIS) {
				s = DisplayFormatters.prettyFormatTimeDiffShort(resources, etaSecs);
			} else if (MapUtils.getMapFloat(item,
					TransmissionVars.FIELD_TORRENT_PERCENT_DONE, -1f) >= 1) {
				float shareRatio = MapUtils.getMapFloat(item,
						TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, -1);
				s = shareRatio < 0 ? ""
						: resources.getString(
						holder.isSmall ? R.string.torrent_row_share_ratio
								: R.string.torrent_row_share_ratio_circle,
						shareRatio);
			}
			flipper.changeText(holder.tvETA, s, holder.animateFlip, validator);
		}
	}
	
	private void fillRateInfo(TorrentListViewHolder holder, Map<?, ?> item, ViewHolderFlipValidator validator) {
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);

			String rateString = rateUpload <= 0 ? "" : "\u25B2 "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload);
			flipper.changeText(holder.tvUlRate, rateString, holder.animateFlip,
					validator);
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);
			String rateString = rateDownload <= 0 ? "" : "\u25BC "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload);
			flipper.changeText(holder.tvDlRate, rateString, holder.animateFlip,
					validator);
		}
	}
	
	private void fillStatusInfo(TorrentListViewHolder holder, Map<?, ?> item, Session session, ViewHolderFlipValidator validator) {
		if (holder.tvStatus != null) {
			List<?> mapTagUIDs = MapUtils.getMapList(item,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			StringBuilder text = new StringBuilder();
			int color = -1;

			if (mapTagUIDs == null || mapTagUIDs.size() == 0) {

				int status = MapUtils.getMapInt(item,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				int id;
				switch (status) {
					case TransmissionVars.TR_STATUS_CHECK_WAIT:
					case TransmissionVars.TR_STATUS_CHECK:
						id = R.string.torrent_status_checking;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD:
						id = R.string.torrent_status_download;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD_WAIT:
						id = R.string.torrent_status_queued_dl;
						break;

					case TransmissionVars.TR_STATUS_SEED:
						id = R.string.torrent_status_seed;
						break;

					case TransmissionVars.TR_STATUS_SEED_WAIT:
						id = R.string.torrent_status_queued_ul;
						break;

					case TransmissionVars.TR_STATUS_STOPPED:
						id = R.string.torrent_status_stopped;
						break;

					default:
						id = -1;
						break;
				}
				if (id >= 0) {
					text.append(context.getString(id));
				}
			} else {
				for (Object o : mapTagUIDs) {
					String name = null;
					int type = 0;
					if (o instanceof Number) {
						Map<?, ?> mapTag = session.tag
								.getTag(((Number) o).longValue());
						if (mapTag != null) {
							String htmlColor = MapUtils.getMapString(mapTag,
									TransmissionVars.FIELD_TAG_COLOR, null);
							if (htmlColor != null && htmlColor.startsWith("#")) {
								color = Integer.decode("0x" + htmlColor.substring(1));
							}
							name = MapUtils.getMapString(mapTag,
									TransmissionVars.FIELD_TAG_NAME, null);
							// English hack.  If we had the tag-id, we could use 3 or 4
							if (name != null && name.startsWith("Queued for")) {
								name = "Queued";
							}
							type = MapUtils.getMapInt(mapTag, TransmissionVars.FIELD_TAG_TYPE,
									0);
						}
					}
					if (type != 2) {
						continue;
					}
					if (name == null) {
						continue;
					}
					if (text.length() > 0) {
						text.append(" ");
					}
					text.append("|");
					text.append(name);
					text.append("|");
				}
			}

			SpannableStringBuilder ss = new SpannableStringBuilder(text);
			String string = text.toString();
			SpanBubbles.setSpanBubbles(ss, string, "|",
					holder.tvStatus.getPaint(), color < 0 ? colorBGTagState : color,
					colorFGTagState, colorBGTagState, null);
			flipper.changeText(holder.tvStatus, ss, holder.animateFlip, validator);
		}
	}
	
	private void fillTagsInfo(TorrentListViewHolder holder, Map<?, ?> item, Session session, ViewHolderFlipValidator validator) {
		if (holder.tvTags != null) {
			ArrayList<Map<?, ?>> listTags = new ArrayList<>();
			List<?> mapTagUIDs = MapUtils.getMapList(item,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			if (mapTagUIDs != null) {
				for (Object o : mapTagUIDs) {
					int type;
					if (o instanceof Number) {
						Map<?, ?> mapTag = session.tag
								.getTag(((Number) o).longValue());
						if (mapTag != null) {
							type = MapUtils.getMapInt(mapTag, TransmissionVars.FIELD_TAG_TYPE,
									0);
							if (type == 2) {
								continue;
							}
							if (type == 1) {
								boolean canBePublic = MapUtils.getMapBoolean(mapTag,
										TransmissionVars.FIELD_TAG_CANBEPUBLIC, false);
								if (!canBePublic) {
									continue;
								}
							}
							listTags.add(mapTag);
						}
					}
				}
			}
			if (listTags.size() > 0) {
				try {
					// TODO: mebbe cache spanTags in holder?
					SpanTags spanTags = new SpanTags(context, session, holder.tvTags,
							null);

					spanTags.setFlipper(flipper, validator);
					spanTags.setShowIcon(false);

					spanTags.setTagMaps(listTags);
					spanTags.updateTags();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			} else {
				flipper.changeText(holder.tvTags, "", holder.animateFlip, validator);
			}
		}
	}

//Refactoring end
	}
}