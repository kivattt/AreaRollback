package com.kiva.arearollback.commands;

import com.fox2code.foxloader.network.ChatColors;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.AreaRollback;

public class RollbackVersion extends CommandCompat {
    public RollbackVersion() {
        super("rollbackversion", false);
    }

    @Override
    public String commandSyntax() {
        return ChatColors.YELLOW + "/rollbackversion";
    }

    @Override
    public void onExecute(String[] args, NetworkPlayer commandExecutor) {
        commandExecutor.displayChatMessage(ChatColors.GREEN + "AreaRollback" + ChatColors.RESET + " is on version " + ChatColors.YELLOW +  AreaRollback.version + ChatColors.RESET);
    }
}
