package com.kiva.arearollback.commands;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.ChatColors;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.AreaRollbackServer;

public class RollbackFlipDimension extends CommandCompat {
    public RollbackFlipDimension() {
        super("rollbackflipdimension", true);
    }

    public String commandSyntax() {
        return ChatColors.YELLOW + "/rollbackflipdimension";
    }

    @Override
    public void onExecute(String[] args, NetworkPlayer commandExecutor) {
        AreaRollbackServer.flipDimensionForRollbacks ^= true;
        ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Rollbacks will now copy from the " + (AreaRollbackServer.flipDimensionForRollbacks ? "opposite" : "same") + " dimension!");
        commandExecutor.displayChatMessage(ChatColors.GREEN + "Rollbacks will now copy from the " + (AreaRollbackServer.flipDimensionForRollbacks ? (ChatColors.RED + "opposite" + ChatColors.GREEN) : "same") + " dimension!");
    }
}
