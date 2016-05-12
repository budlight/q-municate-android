/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quickblox.q_municate.utils.inappbilling;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app product's listing details.
 */
public class SkuDetails {
    private final String mItemType;
    private String mSku;
    private String mType;
    private String mPrice;
    private String mTitle;
    private String mDescription;
    private final String mJson;
    private String mCurrencyCode;
    private Integer mPriceAmountMicros;

    public SkuDetails(String jsonSkuDetails) throws JSONException {
        this(IabHelper.ITEM_TYPE_INAPP, jsonSkuDetails);
    }

    public SkuDetails(String itemType, String jsonSkuDetails) throws JSONException {
        mItemType = itemType;
        mJson = jsonSkuDetails;

        Log.d("SkuDetails", mJson);
        //{"title":"Premium (Fleek)","price":"$9.99","type":"inapp","description":"Premium","price_amount_micros":9990000,"price_currency_code":"USD","productId":"com.promitor.fleek.premium"}
        JSONObject o = new JSONObject(mJson);
        mSku = o.optString("productId");
        mType = o.optString("type");
        mPrice = o.optString("price");
        mTitle = o.optString("title");
        mDescription = o.optString("description");
        mCurrencyCode = o.optString("price_currency_code");
        mPriceAmountMicros = o.optInt("price_amount_micros");

    }

    public String getSku() { return mSku; }
    public String getType() { return mType; }
    public String getPrice() { return mPrice; }
    public String getTitle() { return mTitle; }
    public String getCurrencyCode() { return mCurrencyCode; }
    public Integer getPriceAmountMicros() { return mPriceAmountMicros; }

    public Double getDoublePrice() { return (double)mPriceAmountMicros/1000000; }

    public String getDescription() { return mDescription; }

    @Override
    public String toString() {
        return "SkuDetails:" + mJson;
    }
}
