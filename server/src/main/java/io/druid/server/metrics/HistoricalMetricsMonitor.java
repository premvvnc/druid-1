/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.server.metrics;

import com.google.inject.Inject;
import com.metamx.emitter.service.ServiceEmitter;
import com.metamx.emitter.service.ServiceMetricEvent;
import com.metamx.metrics.AbstractMonitor;
import io.druid.client.DruidServerConfig;
import io.druid.query.DruidMetrics;
import io.druid.server.SegmentManager;
import io.druid.server.coordination.ZkCoordinator;
import io.druid.timeline.DataSegment;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.Map;

public class HistoricalMetricsMonitor extends AbstractMonitor
{
  private final DruidServerConfig serverConfig;
  private final SegmentManager segmentManager;
  private final ZkCoordinator zkCoordinator;

  @Inject
  public HistoricalMetricsMonitor(
      DruidServerConfig serverConfig,
      SegmentManager segmentManager,
      ZkCoordinator zkCoordinator
  )
  {
    this.serverConfig = serverConfig;
    this.segmentManager = segmentManager;
    this.zkCoordinator = zkCoordinator;
  }

  @Override
  public boolean doMonitor(ServiceEmitter emitter)
  {
    emitter.emit(new ServiceMetricEvent.Builder().build("segment/max", serverConfig.getMaxSize()));

    final Object2LongOpenHashMap<String> pendingDeleteSizes = new Object2LongOpenHashMap<>();

    for (DataSegment segment : zkCoordinator.getPendingDeleteSnapshot()) {
      pendingDeleteSizes.addTo(segment.getDataSource(), segment.getSize());
    }

    for (final Object2LongMap.Entry<String> entry : pendingDeleteSizes.object2LongEntrySet()) {

      final String dataSource = entry.getKey();
      final long pendingDeleteSize = entry.getLongValue();
      emitter.emit(
          new ServiceMetricEvent.Builder()
              .setDimension(DruidMetrics.DATASOURCE, dataSource)
              .setDimension("tier", serverConfig.getTier())
              .setDimension("priority", String.valueOf(serverConfig.getPriority()))
              .build("segment/pendingDelete", pendingDeleteSize)
      );
    }

    for (Map.Entry<String, Long> entry : segmentManager.getDataSourceSizes().entrySet()) {
      String dataSource = entry.getKey();
      long used = entry.getValue();

      final ServiceMetricEvent.Builder builder =
          new ServiceMetricEvent.Builder().setDimension(DruidMetrics.DATASOURCE, dataSource)
                                          .setDimension("tier", serverConfig.getTier())
                                          .setDimension("priority", String.valueOf(serverConfig.getPriority()));


      emitter.emit(builder.build("segment/used", used));
      final double usedPercent = serverConfig.getMaxSize() == 0 ? 0 : used / (double) serverConfig.getMaxSize();
      emitter.emit(builder.build("segment/usedPercent", usedPercent));
    }

    for (Map.Entry<String, Long> entry : segmentManager.getDataSourceCounts().entrySet()) {
      String dataSource = entry.getKey();
      long count = entry.getValue();
      final ServiceMetricEvent.Builder builder =
          new ServiceMetricEvent.Builder().setDimension(DruidMetrics.DATASOURCE, dataSource)
                                          .setDimension("tier", serverConfig.getTier())
                                          .setDimension(
                                              "priority",
                                              String.valueOf(serverConfig.getPriority())
                                          );

      emitter.emit(builder.build("segment/count", count));
    }

    return true;
  }
}
