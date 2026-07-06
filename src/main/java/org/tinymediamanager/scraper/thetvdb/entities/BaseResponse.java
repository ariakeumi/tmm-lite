package org.tinymediamanager.scraper.thetvdb.entities;

import com.google.gson.annotations.SerializedName;

public class BaseResponse<T> {

  @SerializedName("status")
  public String status = null;

  @SerializedName("data")
  public T      data   = null;
}
