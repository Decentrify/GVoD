/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.dela.workers.mngr.util;

import com.google.common.base.Optional;
import se.sics.dela.workers.task.DelaWorkTask;
import se.sics.kompics.config.Config;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.Either;
import se.sics.ktoolbox.util.config.options.BasicAddressOption;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.basic.IntIdFactory;
import se.sics.ktoolbox.util.network.basic.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ConfFileTaskReader {

  public static final String TASK_TYPE = "dela.worker.task.type";
  public static final String DATA_ID = "dela.worker.task.dataId";
  public static final BasicAddressOption PARTNER = new BasicAddressOption("dela.worker.task.partner");

  public static Either<DelaWorkTask.LReceiver, DelaWorkTask.LSender> readTask(Config config, IdentifierFactory taskIds) {
    Optional<String> taskTypeOpt = config.readValue(TASK_TYPE, String.class);
    if (!taskTypeOpt.isPresent()) {
      throw new RuntimeException("no task");
    }
    Optional<Integer> dataIdOpt = config.readValue(DATA_ID, Integer.class);
    if (!dataIdOpt.isPresent()) {
      throw new RuntimeException("missing dataId");
    }
    IntIdFactory dataIds = new IntIdFactory(java.util.Optional.empty());
    Identifier dataId = dataIds.id(new BasicBuilders.IntBuilder(dataIdOpt.get()));
    Optional<BasicAddress> partnerOpt = PARTNER.readValue(config);
    if (!partnerOpt.isPresent()) {
      throw new RuntimeException("missing partner");
    }
    switch (taskTypeOpt.get()) {
      case "SENDER":
        return Either.right(new DelaWorkTask.LSender(taskIds.randomId(), partnerOpt.get(), dataId));
      case "RECEIVER":
        return Either.left(new DelaWorkTask.LReceiver(taskIds.randomId(), partnerOpt.get(), dataId));
      default:
        throw new RuntimeException("unknown task");
    }
  }
}