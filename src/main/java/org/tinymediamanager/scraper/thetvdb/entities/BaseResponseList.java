package org.tinymediamanager.scraper.thetvdb.entities;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class BaseResponseList<T> {

  @SerializedName("status")
  public String  status = null;

  @SerializedName("data")
  public List<T> data   = null;
}
