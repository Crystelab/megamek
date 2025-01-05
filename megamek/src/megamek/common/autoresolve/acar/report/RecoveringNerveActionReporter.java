/*
 * Copyright (c) 2025 - The MegaMek Team. All Rights Reserved.
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.common.autoresolve.acar.report;

import megamek.client.ui.swing.util.UIUtil;
import megamek.common.IGame;
import megamek.common.Roll;
import megamek.common.autoresolve.component.Formation;

import java.util.function.Consumer;

import static megamek.client.ui.swing.tooltip.SBFInGameObjectTooltip.ownerColor;

public class RecoveringNerveActionReporter {

    private final IGame game;
    private final Consumer<PublicReportEntry> reportConsumer;

    public RecoveringNerveActionReporter(IGame game, Consumer<PublicReportEntry> reportConsumer) {
        this.reportConsumer = reportConsumer;
        this.game = game;
    }

    public void reportRecoveringNerveStart(Formation formation) {
        reportConsumer.accept(new PublicReportEntry(4500)
            .add(new FormationReportEntry(formation.generalName(), UIUtil.hexColor(ownerColor(formation, game))).text()));
    }

    public void reportToHitValue(int toHitValue) {
        reportConsumer.accept(new PublicReportEntry(4501).add(toHitValue).noNL());
    }

    public void reportSuccessRoll(Roll roll) {
        var report = new PublicReportEntry(4502).indent().noNL();
        report.add(new RollReportEntry(roll).reportText());
        reportConsumer.accept(report);
    }

    public void reportMoraleStatusChange(Formation.MoraleStatus newMoraleStatus) {
        reportConsumer.accept(new PublicReportEntry(4503).add(newMoraleStatus.name()));
    }

    public void reportFailureRoll(Roll roll) {
        reportConsumer.accept(new PublicReportEntry(4504).add(roll.toString()));
    }
}
