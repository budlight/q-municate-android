package com.quickblox.q_municate.ui.activities.purchase;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustEvent;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;
import com.crashlytics.android.answers.PurchaseEvent;
import com.crashlytics.android.answers.StartCheckoutEvent;
import com.quickblox.q_municate.BuildConfig;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.utils.inappbilling.IabHelper;
import com.quickblox.q_municate.utils.inappbilling.IabResult;
import com.quickblox.q_municate.utils.inappbilling.Inventory;
import com.quickblox.q_municate.utils.inappbilling.Purchase;
import com.quickblox.q_municate.utils.inappbilling.SkuDetails;
import com.quickblox.q_municate.ui.activities.base.BaseLoggableActivity;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.models.UserCustomData;
import com.quickblox.q_municate_core.qb.commands.QBUpdateUserCommand;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.Utils;
import com.quickblox.users.model.QBUser;

import java.math.BigDecimal;
import java.util.Currency;

public class InAppPurchaseActivity extends BaseLoggableActivity {



    private Resources resources;
    private QBUser user;

    private IabHelper mHelper;
    private IabHelper.QueryInventoryFinishedListener mReceivedInventoryListener;
    private IabHelper.QueryInventoryFinishedListener mReceivedConsumeInventoryListener;
    private IabHelper.OnConsumeFinishedListener mConsumeFinishedListener;
    private IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener;

    private static final String TAG =
            "fleek.inappbilling";
    //static final String ITEM_SKU = "com.promitor.fleek.premium";
    private static final String ITEM_SKU = "com.promitor.fleek.premium_sub";

    public static void start(Context context) {
        Intent intent = new Intent(context, InAppPurchaseActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected int getContentResId() {
        return R.layout.activity_buy_iap;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buy_iap);

        resources = getResources();
        canPerformLogout.set(false);

        initUI();
        initBroadcastActionList();
        addActions();

        user = AppSession.getSession().getUser();
        startInapp();
        Answers.getInstance().logContentView(new ContentViewEvent()
                .putContentName("InApp Purchase Menu")
                .putContentType("inapp"));
        AdjustEvent event = new AdjustEvent("1fie96");
        Adjust.trackEvent(event);
        WebView webView;
        webView = (WebView)findViewById(R.id.webview_subscriptions);
        webView.getSettings().setJavaScriptEnabled(true);
        class WebAppInterface {

            Context mContext;

            /** Instantiate the interface and set the context */
            WebAppInterface(Context c) {
                mContext = c;
            }



            @JavascriptInterface
            public void buy(String sku) {
                Log.e(TAG, sku.toString());
                //Toast.makeText(mContext, sku, Toast.LENGTH_SHORT).show();
                //com.promitor.fleek.sub_vip_annual
                //com.promitor.fleek.sub_vip
                //com.promitor.fleek.premium_sub_annual
                //com.promitor.fleek.premium_sub
                buySKU(sku);
            }

        }


        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.loadData("", "text/html", null);

        webView.loadUrl("http://panel.socialgamegroup.com/fleek/subscriptions_android/");


    }

    private void initBroadcastActionList() {
        addAction(QBServiceConsts.UPDATE_USER_SUCCESS_ACTION, new UpdateUserSuccessAction());
        addAction(QBServiceConsts.UPDATE_USER_FAIL_ACTION, new UpdateUserFailAction());
    }


    private void startInapp() {
        String base64EncodedPublicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn+o31aQLT5AL5JepSOX6pfy+IUPuOnmvX4MzZtN80MD3lmrWcj/wB7GIRnTa2EhJgvwnx9Anf58a/kqtXbaAZAGlyex0u+tDuvZK43q/qGIXiFVQt8VV+qKwP6iR02q/g5XcrW6Rt3xzTuzBlyQuv/r2ahrD50nfBu/V7e6SAKwTmW8786eRn2NyKho6DxhGOfz2pw912eclLAHMLY8mQ6W1Txui7mLvtzEpNlmrG+yq2zAJTk1F5iIn02T6Md5OM8Wbz2H8DAaQI38ZBiowF9fayX/tSUzXtcUYNrK5Vo99zXSDzdCl8g4ncufGYWUEyzQnzZe6rcDmZ3oUGXM4cQIDAQAB";

        mHelper = new IabHelper(this, base64EncodedPublicKey);

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {


                    Log.d(TAG, "In-app Billing setup failed: " + result);
                    //attach premium button listener now
                } else {


                    Log.d(TAG, "In-app Billing is set up OK");

                }
            }
        });



        mReceivedInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result,
                                                 Inventory inventory) {
                Purchase pur = inventory.getPurchase(ITEM_SKU);

                if (result.isFailure()) {
                    // Handle failure
                    Answers.getInstance().logPurchase(new PurchaseEvent()
                            .putItemName("Premium")
                            .putSuccess(false));

                } else {
                    if (pur != null) {


                        // It is possible to use this to record the ACTUAL currency and revenue via adjust automatically
                        // http://stackoverflow.com/questions/17039119/how-to-get-separate-price-and-currency-information-for-an-in-app-purchase

                        SkuDetails sku = inventory.getSkuDetails(ITEM_SKU);
                        sku.getPrice();

                        Toast.makeText(getApplicationContext(), R.string.buy_premium_enabled, Toast.LENGTH_SHORT).show();

                        //PrefsHelper.getPrefsHelper().savePref(PrefsHelper.PREF_PREMIUM, true);
                        AdjustEvent event = new AdjustEvent("mqrish");
                        event.setRevenue(sku.getDoublePrice(), sku.getCurrencyCode());
                        Adjust.trackEvent(event);
                        Answers.getInstance().logPurchase(new PurchaseEvent()
                                .putItemPrice(BigDecimal.valueOf(sku.getDoublePrice()))
                                .putCurrency(Currency.getInstance(sku.getCurrencyCode()))
                                .putItemName("Premium")
                                .putSuccess(true));
                    }
                }
            }
        };


        mReceivedConsumeInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result,
                                                 Inventory inventory) {

            if (result.isFailure()) {
                // Handle failure
            } else {
                mHelper.consumeAsync(inventory.getPurchase(ITEM_SKU),
                        mConsumeFinishedListener);
            }
            }
        };
        mConsumeFinishedListener =
                new IabHelper.OnConsumeFinishedListener() {
                    public void onConsumeFinished(Purchase purchase,
                                                  IabResult result) {

                    if (result.isSuccess()) {

                    } else {
                        // handle error
                    }
                    }
                };

        mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
            public void onIabPurchaseFinished(IabResult result,
                                              Purchase purchase)
            {
                if (result.isFailure()) {
                    // Handle error
                    return;
                }
                else if (purchase.getSku().equals(ITEM_SKU)) {
                    consumeItem();
                }

            }
        };
    }

    private void consumeItem() {
        //buyPremium.setEnabled(false);
        //restorePremium.setEnabled(false);
        mHelper.queryInventoryAsync(mReceivedInventoryListener);
    }


    private void initUI() {
        //actionBar.setHomeButtonEnabled(true);
        //actionBar.setDisplayHomeAsUpEnabled(true);
        //buyPremium = _findViewById(R.id.upgrade_premium);
        //restorePremium = _findViewById(R.id.restore_premium);
        //buyPremium.setEnabled(false);
        //restorePremium.setEnabled(false);
        if (BuildConfig.DEBUG_MODE) {
            //buyPremium.setEnabled(true);
            //restorePremium.setEnabled(true);
            Log.e(TAG, "debug enabled");


        }
    }
    private void addActions() {
        updateBroadcastActionList();
    }


    public void buySKU(final String sku) {


        if (!mHelper.getAsyncInProgress()) {
            //launch purchase
            Answers.getInstance().logStartCheckout(new StartCheckoutEvent()
                    .putTotalPrice(BigDecimal.valueOf(9.99))
                    .putCurrency(Currency.getInstance("USD"))
                    .putItemCount(1));

            mHelper.launchPurchaseFlow(InAppPurchaseActivity.this, sku, 10001,
                    new IabHelper.OnIabPurchaseFinishedListener() {
                        public void onIabPurchaseFinished(IabResult result,
                                                          Purchase purchase) {
                            if (result.isFailure()) {

                                // Handle error

                                Toast.makeText(getApplicationContext(), R.string.buy_unable_to_complete, Toast.LENGTH_SHORT).show();
                                consumeItem();
                                return;
                            } else if (purchase.getSku().equals("com.promitor.fleek.premium_sub") || purchase.getSku().equals("com.promitor.fleek.premium_sub_annual") ) {
                                consumeItem();
                                UserCustomData userCustomData = Utils.customDataToObject(user.getCustomData());
                                //userCustomData.setPremium(true);
                                userCustomData.setSubscription("premium");

                                user.setCustomData(Utils.customDataToString(userCustomData));
                                QBUpdateUserCommand.start(InAppPurchaseActivity.this, user, null);
                            } else if (purchase.getSku().equals("com.promitor.fleek.sub_vip") || purchase.getSku().equals("com.promitor.fleek.sub_vip_annual") ) {
                                consumeItem();
                                UserCustomData userCustomData = Utils.customDataToObject(user.getCustomData());
                                //userCustomData.setPremium(true);
                                userCustomData.setSubscription("vip");

                                user.setCustomData(Utils.customDataToString(userCustomData));
                                QBUpdateUserCommand.start(InAppPurchaseActivity.this, user, null);
                            }

                        }
                    },
                    "mypurchasetoken");
        }else {
            Log.d(TAG, "Async in progress already..");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null) mHelper.dispose();
        mHelper = null;
    }

    private class UpdateUserFailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception exception = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
        }
    }

    private class UpdateUserSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            QBUser user = (QBUser) bundle.getSerializable(QBServiceConsts.EXTRA_USER);
            AppSession.getSession().updateUser(user);

        }
    }
}