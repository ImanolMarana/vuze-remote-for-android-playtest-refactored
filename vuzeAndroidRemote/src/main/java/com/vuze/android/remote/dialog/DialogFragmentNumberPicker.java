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

package com.vuze.android.remote.dialog;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.android.remote.R;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.util.Thunk;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import eu.rekisoft.android.numberpicker.NumberPicker;

public class DialogFragmentNumberPicker
	extends DialogFragmentBase
{
	private static final String TAG = "NumberPickerDialog";

	private static final String KEY_MIN = "min";

	private static final String KEY_MAX = "max";

	private static final String KEY_VAL = "val";

	private static final String KEY_ID_TITLE = "id_title";

	private static final String KEY_CALLBACK_ID = "callbackID";

	@Thunk
	NumberPickerDialogListener mListener;

	public interface NumberPickerDialogListener
	{
		void onNumberPickerChange(@Nullable String callbackID, int val);
	}

	@Thunk
	int val = 0;

	private int max;

	private int initialVal;

	private int min;

	public static void openDialog(FragmentManager fm, String callbackID,
			String remoteProfileID, int id_title, int currentVal, int min, int max) {
		DialogFragment dlg = new DialogFragmentNumberPicker();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, remoteProfileID);
		bundle.putInt(KEY_MIN, min);
		bundle.putInt(KEY_MAX, max);
		bundle.putInt(KEY_VAL, currentVal);
		if (id_title > 0) {
			bundle.putInt(KEY_ID_TITLE, id_title);
		}
		bundle.putString(KEY_CALLBACK_ID, callbackID);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
    int id_title = R.string.filterby_title;

    Bundle arguments = getArguments();
    if (arguments != null) {
      max = arguments.getInt(KEY_MAX);
      min = arguments.getInt(KEY_MIN);
      initialVal = arguments.getInt(KEY_VAL);
      id_title = arguments.getInt(KEY_ID_TITLE);
    }
    final String callbackID = arguments == null ? null
        : arguments.getString(KEY_CALLBACK_ID);

    if (max <= 0) {
      max = 1024;
    }

    val = Math.max(min, Math.min(max, initialVal));

    AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
        getActivity(), AndroidUtils.isTV() ? R.layout.dialog_number_picker_tv
            : R.layout.dialog_number_picker);

    View view = alertDialogBuilder.view;
    AlertDialog.Builder builder = alertDialogBuilder.builder;

    final NumberPicker numberPicker = (NumberPicker) view.findViewById(
        R.id.number_picker);
    numberPicker.setMinValue(min);
    numberPicker.setMaxValue(max);
    numberPicker.setOnValueChangedListener(
        new NumberPicker.OnValueChangeListener() {
          @Override
          public void onValueChange(NumberPicker picker, int oldVal,
              int newVal) {
            val = numberPicker.getValue();
          }
        });

    numberPicker.setValue(val);

    setupButtons(view, callbackID);

    builder.setTitle(id_title);

    return createDialog(builder, btnSet);
  }

  private void setupButtons(View view, final String callbackID) {
    btnSet = (Button) view.findViewById(R.id.range_set);
    if (btnSet != null) {
      btnSet.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          handleButtonClick(callbackID);
        }
      });
    }

    Button btnClear = (Button) view.findViewById(R.id.range_clear);
    if (btnClear != null) {
      btnClear.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          handleClearButtonClick(callbackID);
        }
      });
    }
  }

  private void handleButtonClick(String callbackID) {
    if (mListener != null) {
      mListener.onNumberPickerChange(callbackID, val);
    }
    DialogFragmentNumberPicker.this.getDialog().dismiss();
  }

  private void handleClearButtonClick(String callbackID) {
    if (mListener != null) {
      mListener.onNumberPickerChange(callbackID, -1);
    }
    DialogFragmentNumberPicker.this.getDialog().dismiss();
  }

  private AlertDialog createDialog(AlertDialog.Builder builder, Button btnSet) {
    if (btnSet == null) {
      // Add action buttons
      builder.setPositiveButton(R.string.action_filterby,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

              if (mListener != null) {
                mListener.onNumberPickerChange(callbackID, val);
              }
            }
          });
      builder.setNeutralButton(R.string.button_clear,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (mListener != null) {
                mListener.onNumberPickerChange(callbackID, -1);
              }
            }
          });
      builder.setNegativeButton(android.R.string.cancel,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              DialogFragmentNumberPicker.this.getDialog().cancel();
            }
          });
    }

    AlertDialog dialog = builder.create();
    Window window = dialog.getWindow();
    if (window != null) {
      window.setSoftInputMode(
          WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    return dialog;
  }
//Refactoring end

		numberPicker.setValue(val);

		Button btnSet = (Button) view.findViewById(R.id.range_set);
		if (btnSet != null) {
			btnSet.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onNumberPickerChange(callbackID, val);
					}
					DialogFragmentNumberPicker.this.getDialog().dismiss();
				}
			});
		}

		Button btnClear = (Button) view.findViewById(R.id.range_clear);
		if (btnClear != null) {
			btnClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onNumberPickerChange(callbackID, -1);
					}
					DialogFragmentNumberPicker.this.getDialog().dismiss();
				}
			});
		}

		builder.setTitle(id_title);

		if (btnSet == null) {
			// Add action buttons
			builder.setPositiveButton(R.string.action_filterby,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {

							if (mListener != null) {
								mListener.onNumberPickerChange(callbackID, val);
							}
						}
					});
			builder.setNeutralButton(R.string.button_clear,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (mListener != null) {
								mListener.onNumberPickerChange(callbackID, -1);
							}
						}
					});
			builder.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							DialogFragmentNumberPicker.this.getDialog().cancel();
						}
					});
		}

		AlertDialog dialog = builder.create();
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(
					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		}

		return dialog;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		Fragment targetFragment = getTargetFragment();
		if (targetFragment instanceof NumberPickerDialogListener) {
			mListener = (NumberPickerDialogListener) targetFragment;
		} else if (context instanceof NumberPickerDialogListener) {
			mListener = (NumberPickerDialogListener) context;
		} else {
			Log.e(TAG, "No Target Fragment " + targetFragment);
		}

	}

	@Override
	public String getLogTag() {
		return TAG;
	}
}
