package com.teletalker.app.subscription;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PaddleApiService {

    @GET("products/{product_id}")
    Call<PaddleProductWithPricesResponse> getProductWithPrices(
            @Path("product_id") String productId,
            @Query("include") String include
    );
    @POST("checkout")
    Call<CheckoutResponse> createCheckout(@Body CheckoutRequest request);


    /**
     * Get single product details
     */
    @GET("products/{product_id}")
    Call<PaddleProductResponse> getProduct(
            @Path("product_id") String productId
    );

    /**
     * Get single price details
     */
    @GET("prices/{price_id}")
    Call<PaddlePriceResponse> getPrice(
            @Path("price_id") String priceId
    );
}