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

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 「/itemkill」コマンドの実際の動作 (Folia 完全対応版)。
 *
 * <p><b>オリジナルのバグ:</b> コマンド実行スレッドから全ワールドのエンティティに直接
 * {@code item.remove()} を呼んでいた。Folia ではコマンドは実行者を所有するリージョンの
 * スレッド (コンソールの場合はグローバルリージョン) で実行されるため、他リージョンが
 * 所有するエンティティの変更はスレッドチェック違反となり、例外やデータ破損の原因になる。</p>
 *
 * <p><b>修正内容:</b></p>
 * <ul>
 *   <li>エンティティ一覧の取得 ({@code World#getEntities()}) はスナップショットを返す
 *       スレッドセーフな API なので、そのまま列挙に使用する。</li>
 *   <li>現在のリージョンが所有するアイテム ({@link Bukkit#isOwnedByCurrentRegion(Entity)})
 *       はその場で削除する。</li>
 *   <li>それ以外のアイテムは {@link io.papermc.paper.threadedregions.scheduler.EntityScheduler}
 *       で「そのエンティティを所有するリージョンのスレッド」に削除処理を移動させてから
 *       削除する。EntityScheduler へのタスク登録自体は任意のスレッドから安全に行える。</li>
 *   <li>削除は複数リージョンにまたがって非同期に完了するため、カウンタは
 *       {@link AtomicInteger} で集計し、全タスク完了後に実行者へ結果を報告する。</li>
 * </ul>
 *
 * <p>この実装は Folia のリージョンスケジューラ API のみを使用しており、
 * 同じ API が実装されている通常の Paper 26.3 でもそのまま動作する。</p>
 */
public final class ItemKillExecutor {

    private final JavaPlugin plugin;

    public ItemKillExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandContext<CommandSourceStack> context) {
        final CommandSender sender = context.getSource().getSender();

        // 全ワールドから落ちているアイテム (Item エンティティ) のスナップショットを収集する。
        // World#getEntities() / Bukkit#getWorlds() はコピーを返すため、どのスレッドからでも安全。
        final List<Item> items = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    items.add(item);
                }
            }
        }

        if (items.isEmpty()) {
            sender.sendMessage(Component.text(
                    "落ちているアイテムが無かったためアイテムを削除できませんでした。", NamedTextColor.GRAY));
            return;
        }

        // 実際に削除できた数と、未完了のタスク数 (リージョンをまたいで非同期に更新される)
        final AtomicInteger removedCount = new AtomicInteger(0);
        final AtomicInteger pending = new AtomicInteger(items.size());

        // 1 アイテム分の処理が完了するたびに呼ばれる。最後の 1 件が完了したら結果を報告する。
        // CommandSender#sendMessage(Component) はスレッドセーフなので、どのリージョンの
        // スレッドから呼んでも問題ない。
        final Runnable onItemDone = () -> {
            if (pending.decrementAndGet() == 0) {
                final int count = removedCount.get();
                if (count > 0) {
                    sender.sendMessage(Component.text(
                            "落ちているアイテムを" + count + "個削除しました", NamedTextColor.GRAY));
                } else {
                    // 削除タスク実行前に全て拾われる/デスポーンした場合など
                    sender.sendMessage(Component.text(
                            "落ちているアイテムが無かったためアイテムを削除できませんでした。", NamedTextColor.GRAY));
                }
            }
        };

        for (Item item : items) {
            if (Bukkit.isOwnedByCurrentRegion(item)) {
                // 現在のリージョンスレッドがこのアイテムを所有している → その場で削除してよい
                if (item.isValid()) {
                    item.remove();
                    removedCount.incrementAndGet();
                }
                onItemDone.run();
            } else {
                // 他リージョンが所有するアイテム → 所有リージョンのスレッドに削除処理を移す。
                // run() が null を返した場合はエンティティが既に除去済み (scheduler retired)。
                // retired コールバックはタスク実行前にエンティティが消えた場合に呼ばれる。
                final ScheduledTask task = item.getScheduler().run(plugin, scheduled -> {
                    // ここはアイテムを所有するリージョンのスレッド上で実行される
                    if (item.isValid()) {
                        item.remove();
                        removedCount.incrementAndGet();
                    }
                    onItemDone.run();
                }, onItemDone);
                if (task == null) {
                    onItemDone.run();
                }
            }
        }
    }
}
