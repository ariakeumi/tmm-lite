package org.tinymediamanager.scraper.thetvdb.entities;

import com.google.gson.annotations.SerializedName;

public class BaseResponseArray<T> {

  @SerializedName("status")
  public String status = null;

  @SerializedName("data")
  public T[]    data   = null;
}
