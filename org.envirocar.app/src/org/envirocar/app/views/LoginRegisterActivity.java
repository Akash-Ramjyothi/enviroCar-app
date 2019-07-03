/**
 * Copyright (C) 2013 - 2019 the enviroCar community
 * <p>
 * This file is part of the enviroCar app.
 * <p>
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app.views;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Pair;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;

import org.envirocar.app.R;
import org.envirocar.app.handler.DAOProvider;
import org.envirocar.app.handler.TrackDAOHandler;
import org.envirocar.app.handler.UserHandler;
import org.envirocar.app.handler.agreement.AgreementManager;
import org.envirocar.app.injection.BaseInjectorActivity;
import org.envirocar.app.main.BaseApplicationComponent;
import org.envirocar.core.entity.TermsOfUse;
import org.envirocar.core.entity.User;
import org.envirocar.core.entity.UserImpl;
import org.envirocar.core.exception.DataUpdateFailureException;
import org.envirocar.core.exception.ResourceConflictException;
import org.envirocar.core.logging.Logger;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * TODO JavaDoc
 *
 * @author dewall
 */
public class LoginRegisterActivity extends BaseInjectorActivity {
    private static final Logger LOG = Logger.getLogger(LoginRegisterActivity.class);

    @BindView(R.id.activity_login_toolbar)
    protected Toolbar mToolbar;
    @BindView(R.id.activity_login_exp_toolbar)
    protected Toolbar mExpToolbar;

    @BindView(R.id.activity_login_card)
    protected CardView mLoginCard;
    @BindView(R.id.activity_account_login_card_username_text)
    protected EditText mLoginUsername;
    @BindView(R.id.activity_account_login_card_password_text)
    protected EditText mLoginPassword;

    @BindView(R.id.activity_register_card)
    protected CardView mRegisterCard;
    @BindView(R.id.activity_account_register_email_input)
    protected EditText mRegisterEmail;
    @BindView(R.id.activity_account_register_username_input)
    protected EditText mRegisterUsername;
    @BindView(R.id.activity_account_register_password_input)
    protected EditText mRegisterPassword;
    @BindView(R.id.activity_account_register_password2_input)
    protected EditText mRegisterPassword2;
    @BindView(R.id.activity_account_register_agree_tou_checbox)
    protected CheckBox mAcceptTouCheckbox;
    //    @BindView(R.id.activity_account_register_agree_privacy_checbox)
//    protected CheckBox mAcceptPrivacyCheckbox;
    @BindView(R.id.activity_account_register_agree_tou_text)
    protected TextView mAcceptedTouText;

    @Inject
    protected UserHandler mUserManager;
    @Inject
    protected DAOProvider mDAOProvider;
    @Inject
    protected AgreementManager mAgreementManager;
    @Inject
    protected TrackDAOHandler mTrackDAOHandler;

    private final Scheduler.Worker mMainThreadWorker = AndroidSchedulers
            .mainThread().createWorker();
    private final Scheduler.Worker mBackgroundWorker = Schedulers
            .newThread().createWorker();

    private Subscription mLoginSubscription;
    private Subscription mRegisterSubscription;
    private Subscription mTermsOfUseSubscription;

    @Override
    protected void injectDependencies(BaseApplicationComponent baseApplicationComponent) {
        baseApplicationComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        // Inject the Views.
        ButterKnife.bind(this);

        // Initializes the Toolbar.
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("Account");
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        expandExpToolbarToHalfScreen();
        if (intent.getStringExtra("from").equalsIgnoreCase("login")) {
            slideInLoginCard();
        } else {
            slideInRegisterCard();
        }

        List<Pair<String, View.OnClickListener>> clickableStrings = Arrays.asList(
                new Pair<>("Terms and Conditions", v -> {
                    LOG.info("Terms and Conditions clicked. Showing dialog");
                    showTermsOfUseDialog();
                }),
                new Pair<>("Privacy Policy", v -> {
                    LOG.info("Privacy Policy clicked. Showing dialog");
                    showTermsOfUseDialog();
                })
        );
        makeTextLinks(mAcceptedTouText, clickableStrings);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackPressed();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mRegisterCard != null && mRegisterCard.getVisibility() == View.VISIBLE) {
            animateViewTransition(mRegisterCard, R.anim.translate_slide_out_right_card, true);
            animateViewTransition(mLoginCard, R.anim.translate_slide_in_left_card, false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // If a login process is in progress, then
        // unsubscribe the subscription and finish the thread.
        if (mLoginSubscription != null && !mLoginSubscription.isUnsubscribed()) {
            mLoginSubscription.unsubscribe();
            mLoginSubscription = null;
        }
        // same for the registration process.
        if (mRegisterSubscription != null && mRegisterSubscription.isUnsubscribed()) {
            mRegisterSubscription.unsubscribe();
            mRegisterSubscription = null;
        }
    }

    /**
     * Login routine.
     */
    @OnClick(R.id.activity_account_login_card_login_button)
    protected void onLoginButtonClicked() {
        // Reset errors.
        mLoginUsername.setError(null);
        mLoginPassword.setError(null);

        // Store values at the time of the login attempt.
        String username = mLoginUsername.getText().toString();
        String password = mLoginPassword.getText().toString();

        View focusView = null;

        // Check for a valid password.
        if (password == null || password.isEmpty() || password.equals("")) {
            mLoginPassword.setError(getString(R.string.error_field_required));
            focusView = mLoginPassword;
        }

        // Check if the password is too short.
        else if (password.length() < 6) {
            mLoginPassword.setError(getString(R.string.error_invalid_password));
            focusView = mLoginPassword;
        }

        // Check for a valid username.
        if (username == null || username.isEmpty() || username.equals("")) {
            mLoginUsername.setError(getString(R.string.error_field_required));
            focusView = mLoginUsername;
        }

        if (focusView != null) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        }
        // If the input values are valid, then try to login.
        else {
            // hide the keyboard
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mLoginPassword.getWindowToken(), 0);

            // Create a dialog indicating the log in process.
            final MaterialDialog dialog = new MaterialDialog.Builder(LoginRegisterActivity.this)
                    .title(R.string.activity_login_logging_in_dialog_title)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();

            mLoginSubscription = mBackgroundWorker.schedule(() -> {
                mUserManager.logIn(username, password, new UserHandler.LoginCallback() {
                    @Override
                    public void onSuccess(User user) {
                        dialog.dismiss();
                        // Successfully logged in.
                        mMainThreadWorker.schedule(() -> {
                            // If any error occurs, then set the focus on the error.
                            if (user == null) {
                                if (mLoginUsername.getError() != null)
                                    mLoginUsername.requestFocus();
                                else
                                    mLoginPassword.requestFocus();
                                return;
                            }

                            // First, show a snackbar.
                            Snackbar.make(mExpToolbar,
                                    String.format(getResources().getString(
                                            R.string.welcome_message), user.getUsername()),
                                    Snackbar.LENGTH_LONG)
                                    .show();

                            finish();
                            // Then ask for terms of use acceptance.
                            // askForTermsOfUseAcceptance();
                        });
                    }

                    @Override
                    public void onPasswordIncorrect(String password) {
                        dialog.dismiss();
                        mMainThreadWorker.schedule(() ->
                                mLoginPassword.setError(
                                        getString(R.string.error_incorrect_password)));
                    }

                    @Override
                    public void onMailNotConfirmed() {
                        dialog.dismiss();
                        mMainThreadWorker.schedule(() ->
                                new MaterialDialog.Builder(LoginRegisterActivity.this)
                                        .cancelable(true)
                                        .positiveText(R.string.ok)
                                        .title("Email Confirmation Required")
                                        .content("Your Email account has not been confirmed yet. To use the full functionality of enviroCar, please log into your email account and confirm your email address.")
                                        .build().show());
                    }

                    @Override
                    public void onUnableToCommunicateServer() {
                        dialog.dismiss();
                        mMainThreadWorker.schedule(() ->
                                mLoginPassword.setError(
                                        getString(R.string.error_host_not_found)));
                    }
                });
            });
        }
    }

    private void showTermsOfUseDialog() {
        LOG.info("Show Terms of Use Dialog");
        mAgreementManager.showLatestTermsOfUseDialogObservable(this)
                .subscribe(tou -> LOG.info("Closed Dialog"));
    }

    private void showPrivacyStatementDialog() {
        LOG.info("Show Privacy Statement dialog");

    }

    private void askForTermsOfUseAcceptance() {
        // Unsubscribe before issueing a new request.
        if (mTermsOfUseSubscription != null && !mTermsOfUseSubscription.isUnsubscribed())
            mTermsOfUseSubscription.unsubscribe();

        mTermsOfUseSubscription = mAgreementManager.verifyTermsOfUse(LoginRegisterActivity.this)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<TermsOfUse>() {
                    @Override
                    public void onStart() {
                        LOG.info("onStart() verifying terms of use");
                    }

                    @Override
                    public void onCompleted() {
                        LOG.info("onCompleted() verifying terms of use");
                    }

                    @Override
                    public void onError(Throwable e) {
                        LOG.warn(e.getMessage(), e);
                    }

                    @Override
                    public void onNext(TermsOfUse termsOfUse) {
                        LOG.info(String.format(
                                "User has accepted the terms of use -> [%s]",
                                termsOfUse.getIssuedDate()));
                    }
                });
    }

    /**
     * Register routine
     */
    @OnClick(R.id.activity_account_register_button)
    protected void onRegisterAccountButtonClicked() {
        mRegisterUsername.setError(null);
        mRegisterEmail.setError(null);
        mRegisterPassword.setError(null);
        mRegisterPassword2.setError(null);

        // We do not want to have dublicate registration processes.
        if (mRegisterSubscription != null && !mRegisterSubscription.isUnsubscribed())
            return;

        // Get all the values of the edittexts
        final String username = mRegisterUsername.getText().toString();
        final String email = mRegisterEmail.getText().toString();
        final String password = mRegisterPassword.getText().toString();
        final String password2 = mRegisterPassword2.getText().toString();

        View focusView = null;
        // Check for valid passwords.
        if (password == null || password.isEmpty() || password.equals("")) {
            mRegisterPassword.setError(getString(R.string.error_field_required));
            focusView = mRegisterPassword;
        } else if (mRegisterPassword.length() < 6) {
            mRegisterPassword.setError(getString(R.string.error_invalid_password));
            focusView = mRegisterPassword;
        }

        // check if the password confirm is empty
        if (password2 == null || password2.isEmpty() || password2.equals("")) {
            mRegisterPassword2.setError(getString(R.string.error_field_required));
            focusView = mRegisterPassword2;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mRegisterEmail.setError(getString(R.string.error_field_required));
            focusView = mRegisterEmail;
        } else if (!email.matches("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\" +
                ".[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$")) {
            mRegisterEmail.setError(getString(R.string.error_invalid_email));
            focusView = mRegisterEmail;
        }

        // check for valid username
        if (username == null || username.isEmpty() || username.equals("")) {
            mRegisterUsername.setError(getString(R.string.error_field_required));
            focusView = mRegisterUsername;
        } else if (username.length() < 6) {
            mRegisterUsername.setError(getString(R.string.error_invalid_username));
            focusView = mRegisterUsername;
        }

        // check if passwords match
        if (!password.equals(password2)) {
            mRegisterPassword2.setError(getString(R.string.error_passwords_not_matching));
            focusView = mRegisterPassword2;
        }

        // check if tou and privacy statement have been accepted.
        if (!mAcceptTouCheckbox.isChecked()) {
            mAcceptTouCheckbox.setError("some error");
            focusView = mAcceptTouCheckbox;
        }
//        if (!mAcceptPrivacyCheckbox.isChecked()) {
//            mAcceptPrivacyCheckbox.setError("some error");
//        }

        // Check if an error occured.
        if (focusView != null) {
            // There was an error; don't attempt register and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            //hide the keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mRegisterPassword.getWindowToken(), 0);

            // TODO
            //            mRegisterStatusMessageView.setText(R.string.register_progress_signing_in);

            // Show a progress spinner, and kick off a pground task to
            // perform the user register attempt.
            final MaterialDialog dialog = new MaterialDialog.Builder(LoginRegisterActivity.this)
                    .title(R.string.register_progress_signing_in)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();

            mBackgroundWorker.schedule(() -> {
                try {
                    User newUser = new UserImpl(username, password);
                    newUser.setMail(email);
                    mDAOProvider.getUserDAO().createUser(newUser);

                    // Successfully created the user
                    mMainThreadWorker.schedule(() -> {
                        // Dismiss the progress dialog.
                        dialog.dismiss();

                        final MaterialDialog d = new MaterialDialog.Builder(LoginRegisterActivity.this)
                                .title("You are just one step away....")
                                .content("You are just one step away from activating your account on enviroCar. Please check your mails in order to complete the registration.")
                                .cancelable(false)
                                .positiveText(R.string.ok)
                                .cancelListener(dialog1 -> {
                                    LOG.info("canceled");
                                    finish();
                                })
                                .onAny((a, b) -> {
                                    LOG.info("onPositive");
                                    finish();
                                })
                                .show();
                    });

//                    finish();
                    // askForTermsOfUseAcceptance();
                } catch (ResourceConflictException e) {
                    LOG.warn(e.getMessage(), e);

                    // Show an error. // TODO show error in a separate error text view.
                    mMainThreadWorker.schedule(() -> {
                        mRegisterUsername.setError(getString(
                                R.string.error_username_already_in_use));
                        mRegisterEmail.setError(getString(
                                R.string.error_email_already_in_use));
                        mRegisterUsername.requestFocus();
                    });

                    // Dismuss the progress dialog.
                    dialog.dismiss();
                } catch (DataUpdateFailureException e) {
                    LOG.warn(e.getMessage(), e);

                    // Show an error.
                    mMainThreadWorker.schedule(() -> {
                        mRegisterUsername.setError(getString(R.string.error_host_not_found));
                        mRegisterUsername.requestFocus();
                    });

                    // Dismiss the progress dialog.
                    dialog.dismiss();
                }
            });
        }
    }

    /**
     * OnClick annotated function that gets invoked when the register button on the login card
     * gets clicked.
     */
    @OnClick(R.id.activity_account_login_card_register_button)
    protected void onRegisterButtonClicked() {
        // When the register button was clicked, then replace the login card with the
        // registration card.
        animateViewTransition(mLoginCard, R.anim.translate_slide_out_left_card, true);
        animateViewTransition(mRegisterCard, R.anim.translate_slide_in_right_card, false);
    }

    @OnClick(R.id.activity_account_register_card_signin_button)
    protected void onSignInButtonClicked() {
        // When the register button was clicked, then replace the login card with the
        // registration card.
        animateViewTransition(mRegisterCard, R.anim.translate_slide_out_right_card, true);
        animateViewTransition(mLoginCard, R.anim.translate_slide_in_left_card, false);
    }

    /**
     * Applies an animation on the given view.
     *
     * @param view         the view to apply the animation on.
     * @param animResource the animation resource.
     * @param hide         should the view be hid?
     */
    private void animateViewTransition(final View view, int animResource, boolean hide) {
        Animation animation = AnimationUtils.loadAnimation(this, animResource);
        if (hide) {
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // nothing to do..
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    view.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    // nothing to do..
                }
            });
            view.startAnimation(animation);
        } else {
            view.setVisibility(View.VISIBLE);
            view.startAnimation(animation);
        }
    }

    private void slideInLoginCard() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.translate_in_bottom_login_card);
        mLoginCard.setVisibility(View.VISIBLE);
        mLoginCard.startAnimation(animation);
    }

    private void slideInRegisterCard() {
        Animation animation = AnimationUtils.loadAnimation(this,
                R.anim.translate_in_bottom_login_card);
        mRegisterCard.setVisibility(View.VISIBLE);
        mRegisterCard.startAnimation(animation);
    }

    private void makeTextLinks(TextView text, List<Pair<String, View.OnClickListener>> links) {
        SpannableString string = new SpannableString(text.getText());
        for (Pair<String, View.OnClickListener> link : links) {
            ClickableSpan span = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Selection.setSelection((Spannable) ((TextView) widget).getText(), 0);
                    widget.invalidate();
                    link.second.onClick(widget);
                }
            };

            int start = text.getText().toString().indexOf(link.first);
            if (start > 0) {
                string.setSpan(span, start, start + link.first.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        text.setMovementMethod(LinkMovementMethod.getInstance());
        text.setText(string, TextView.BufferType.SPANNABLE);
    }

    /**
     * Expands the expanding toolbar to the a specific amount of the screensize.
     */
    private void expandExpToolbarToHalfScreen() {
        mExpToolbar.setVisibility(View.VISIBLE);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int height = size.y;

        ValueAnimator animator = createSlideAnimator(0, height / 3);
        animator.setDuration(600);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // nothing to do..
            }

            @Override
            public void onAnimationEnd(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
    }


    /**
     * Constructs and returns a ValueAnimator that animates between int values.
     *
     * @param start start value
     * @param end   end value
     * @return the ValueAnimator that animates the desired animation.
     */
    private ValueAnimator createSlideAnimator(int start, int end) {

        ValueAnimator animator = ValueAnimator.ofInt(start, end);

        animator.addUpdateListener(animation -> {
            int value = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = mExpToolbar.getLayoutParams();
            layoutParams.height = value;
            mExpToolbar.setLayoutParams(layoutParams);
        });

        return animator;
    }
}
