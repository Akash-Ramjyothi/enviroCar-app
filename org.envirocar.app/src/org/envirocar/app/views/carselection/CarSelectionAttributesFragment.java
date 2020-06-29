package org.envirocar.app.views.carselection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jakewharton.rxbinding3.widget.RxTextView;

import org.envirocar.app.BaseApplicationComponent;
import org.envirocar.app.R;
import org.envirocar.app.injection.BaseInjectorFragment;
import org.envirocar.core.entity.Vehicles;
import org.envirocar.core.logging.Logger;
import org.envirocar.storage.EnviroCarVehicleDB;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class CarSelectionAttributesFragment extends BaseInjectorFragment {

    @BindView(R.id.fragment_attributes_manufacturer_input)
    protected AutoCompleteTextView manufactureEditText;
    @BindView(R.id.fragment_attributes_model_input)
    protected AutoCompleteTextView modelEditText;
    @BindView(R.id.fragment_attributes_year_input)
    protected AutoCompleteTextView yearEditText;

    @Inject
    EnviroCarVehicleDB enviroCarVehicleDB;
    private static final Logger LOG = Logger.getLogger(CarSelectionAttributesFragment.class);
    private Set<String> mManufacturerNames = new HashSet<>();
    private Map<String, Set<String>> mCarToModelMap = new ConcurrentHashMap<>();
    private Map<String, Set<String>> mModelToYear = new ConcurrentHashMap<>();
    private Scheduler.Worker mainThreadWorker = AndroidSchedulers.mainThread().createWorker();
    private CompositeDisposable disposable = new CompositeDisposable();
    private static final int ERROR_DEBOUNCE_TIME = 750;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_car_selection_attributes, container, false);
        ButterKnife.bind(this, view);
        fetchVehicles();
        initFocusChangedListener();
        reactiveTexFieldCheck();
        manufactureEditText.setOnItemClickListener((parent, view1, position, id) -> requestNextTextFieldFocus(manufactureEditText));
        modelEditText.setOnItemClickListener((parent, view12, position, id) -> requestNextTextFieldFocus(modelEditText));
        yearEditText.setOnItemClickListener((parent, view13, position, id) -> requestNextTextFieldFocus(yearEditText));
        return view;
    }

    @Override
    protected void injectDependencies(BaseApplicationComponent baseApplicationComponent) {
        baseApplicationComponent.inject(this);
    }

    @OnTextChanged(value = R.id.fragment_attributes_manufacturer_input, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    protected void onManufacturerChanged() {
        manufactureEditText.setError(null);
        modelEditText.setText("");
        yearEditText.setText("");
    }

    @OnTextChanged(value = R.id.fragment_attributes_model_input, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    protected void onModelChanged() {
        modelEditText.setError(null);
        yearEditText.setText("");
    }

    @OnTextChanged(value = R.id.fragment_attributes_year_input, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    protected void onYearChanged() {
        yearEditText.setError(null);
    }

    @OnClick(R.id.fragment_car_search_button)
    void searchButtonClick() {
        String manufacturer = manufactureEditText.getText().toString().trim();
        String model = modelEditText.getText().toString().trim();
        String year = yearEditText.getText().toString().trim();
        View focusView = null;
        if (manufacturer.isEmpty()) {
            manufactureEditText.setError("empty values");
            focusView = manufactureEditText;
        }

        if (model.isEmpty()) {
            modelEditText.setError("empty values");
            focusView = modelEditText;
        }

        if (year.isEmpty()) {
            yearEditText.setError("empty values");
            focusView = yearEditText;
        }

        //focus on last and stop searching and return
        if (focusView != null) {
            focusView.requestFocus();
            return;
        }

        // also stop searching if already error becuase of values not in list
        if (manufactureEditText.getError() != null || modelEditText.getError() != null|| yearEditText.getError() != null)
            return;


    }

    private void fetchVehicles() {
        Single<List<Vehicles>> vehicle = enviroCarVehicleDB.vehicleDAO().getManufacturerVehicles();
        vehicle.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribeWith(new DisposableSingleObserver<List<Vehicles>>() {
                    @Override
                    public void onSuccess(List<Vehicles> vehicles) {
                        for (Vehicles vehicles1 : vehicles) {
                            addCarToAutocompleteList(vehicles1);
                        }
                        mainThreadWorker.schedule(() -> {
                            updateManufacturerView();
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        LOG.error(e.getMessage(), e);
                    }
                });
    }

    private void addCarToAutocompleteList(Vehicles vehicle) {
        mManufacturerNames.add(vehicle.getManufacturer());

        if (!mCarToModelMap.containsKey(vehicle.getManufacturer()))
            mCarToModelMap.put(vehicle.getManufacturer(), new HashSet<>());
        mCarToModelMap.get(vehicle.getManufacturer()).add(vehicle.getCommerical_name());

        if (!mModelToYear.containsKey(vehicle.getCommerical_name()))
            mModelToYear.put(vehicle.getCommerical_name(), new HashSet<>());
        int year = ((CarSelectionActivity) getActivity()).convertDateToInt(vehicle.getAllotment_date());
        String yearString = Integer.toString(year);
        mModelToYear.get(vehicle.getCommerical_name()).add(yearString);
    }

    private void updateManufacturerView() {
        if (!mManufacturerNames.isEmpty()) {
            manufactureEditText.setAdapter(((CarSelectionActivity) getActivity()).sortedAdapter(getContext(), mManufacturerNames));
        } else {
            manufactureEditText.setAdapter(null);
        }
    }

    private void updateModelView(String manufacturer) {
        if (mCarToModelMap.containsKey(manufacturer)) {
            modelEditText.setAdapter(((CarSelectionActivity) getActivity()).sortedAdapter(getContext(), mCarToModelMap.get(manufacturer)));
        } else {
            modelEditText.setAdapter(null);
        }
    }

    private void updateYearView(String model) {
        if (mModelToYear.containsKey(model)) {
            yearEditText.setAdapter(((CarSelectionActivity) getActivity()).sortedAdapter(getContext(), mModelToYear.get(model)));
        } else {
            yearEditText.setAdapter(null);
        }
    }

    private void initFocusChangedListener() {
        manufactureEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String manufacturer = manufactureEditText.getText().toString();
                updateModelView(manufacturer);
            }
        });
        modelEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String model = modelEditText.getText().toString();
                updateYearView(model);
            }
        });
    }

    private void requestNextTextFieldFocus(TextView textField) {
        try {
            TextView nextField = (TextView) textField.focusSearch(View.FOCUS_DOWN);
            nextField.requestFocus();
        } catch (Exception e) {
            LOG.warn("Unable to find next field or to request focus to next field.");
        }
    }

    private void reactiveTexFieldCheck() {
        disposable.add(RxTextView.textChanges(manufactureEditText)
                .skipInitialValue()
                .debounce(ERROR_DEBOUNCE_TIME, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .map(t -> t.toString())
                .subscribe(manufacture -> {
                    ListAdapter adapter = manufactureEditText.getAdapter();
                    int flag = 0;
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (adapter.getItem(i).toString().compareTo(manufacture) == 0) {
                            flag = 1;
                            break;
                        }
                    }
                    if (flag == 0) {
                        manufactureEditText.setError("Not in list");
                        manufactureEditText.requestFocus();
                    } else {
                        manufactureEditText.setError(null);
                    }
                }));

        disposable.add(RxTextView.textChanges(modelEditText)
                .skipInitialValue()
                .debounce(ERROR_DEBOUNCE_TIME, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .map(t -> t.toString())
                .subscribe(model -> {
                    try {
                        ListAdapter adapter = modelEditText.getAdapter();
                        int flag = 0;
                        for (int i = 0; i < adapter.getCount(); i++) {
                            if (adapter.getItem(i).toString().compareTo(model) == 0) {
                                flag = 1;
                                break;
                            }
                        }

                        if (flag == 0) {
                            modelEditText.setError("Not in list");
                            modelEditText.requestFocus();
                        } else {
                            modelEditText.setError(null);
                        }
                    } catch (Exception e) {
                    }
                }));

        disposable.add(RxTextView.textChanges(yearEditText)
                .skipInitialValue()
                .debounce(ERROR_DEBOUNCE_TIME, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .map(t -> t.toString())
                .subscribe(year -> {
                    try {
                        ListAdapter adapter = yearEditText.getAdapter();
                        int flag = 0;
                        for (int i = 0; i < adapter.getCount(); i++) {
                            if (adapter.getItem(i).toString().compareTo(year) == 0) {
                                flag = 1;
                                break;
                            }
                        }

                        if (flag == 0) {
                            yearEditText.setError("Not in list");
                            yearEditText.requestFocus();
                        } else {
                            yearEditText.setError(null);
                        }
                    } catch (Exception e) {
                    }
                }));
    }
}
