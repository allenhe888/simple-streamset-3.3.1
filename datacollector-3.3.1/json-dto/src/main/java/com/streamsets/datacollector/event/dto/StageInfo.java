/*
 * Copyright 2017 StreamSets Inc.
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
package com.streamsets.datacollector.event.dto;

public class StageInfo {

  private String stageName;
  private int stageVersion;
  private String libraryName;

  public StageInfo() {
  }

  public StageInfo(String stageName, int stageVersion, String libraryName) {
    this.stageName = stageName;
    this.stageVersion = stageVersion;
    this.libraryName = libraryName;
  }

  public String getStageName() {
    return stageName;
  }
  public void setStageName(String stageName) {
    this.stageName = stageName;
  }
  public int getStageVersion() {
    return stageVersion;
  }
  public void setStageVersion(int stageVersion) {
    this.stageVersion = stageVersion;
  }
  public String getLibraryName() {
    return libraryName;
  }
  public void setLibraryName(String libraryName) {
    this.libraryName = libraryName;
  }

}
