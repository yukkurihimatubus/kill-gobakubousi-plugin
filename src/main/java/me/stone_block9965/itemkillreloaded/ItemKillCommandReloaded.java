//        ItemKillCommand-Reloaded
//        Copyright (C) 2026  stone_block9965
//
//        Based on "kill Gobakubousi plugin" (ItemKillCommand)
//        Copyright (C) 2026  yukkurihimatubus
//        https://github.com/yukkurihimatubus/kill-gobakubousi-plugin
//
//        This program is free software: you can redistribute it and/or modify
//        it under the terms of the GNU General Public License as published by
//        the Free Software Foundation, either version 3 of the License, or
//        (at your option) any later version.
//
//        This program is distributed in the hope that it will be useful,
//        but WITHOUT ANY WARRANTY; without even the implied warranty of
//        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//        GNU General Public License for more details.
//
//        You should have received a copy of the GNU General Public License
//        along with this program.  If not, see https://www.gnu.org/licenses/.

package me.stone_block9965.itemkillreloaded;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ItemKillCommand-Reloaded のメインクラス。
 *
 * <p>オリジナル (kill Gobakubousi plugin) を Minecraft 26.3 / Folia 向けに移植し、
 * リージョンスレッドセーフな実装に修正したもの。</p>
 */
public final class ItemKillCommandReloaded extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("ItemKillCommand-Reloaded が正常に読み込まれました");
        getLogger().info("開発: stone_block9965");
        getLogger().info("オリジナル: yukkurihimatubus (kill Gobakubousi plugin)");
        getLogger().info("ほんのちょっとだけ手助け: 1510ty");

        final ItemKillExecutor executor = new ItemKillExecutor(this);

        // コマンドを登録 (Paper / Folia の Brigadier Lifecycle API)
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register(
                        Commands.literal("itemkill")
                                // 権限を持たないプレイヤーにはコマンド自体が見えなくなる
                                .requires(source -> source.getSender().hasPermission("itemkill.use"))
                                .executes(context -> {
                                    executor.execute(context);
                                    return 1;
                                })
                                .build(),
                        "落ちているアイテムを削除"
                )
        );
    }
}
