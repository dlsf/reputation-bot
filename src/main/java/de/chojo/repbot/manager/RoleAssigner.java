package de.chojo.repbot.manager;

import de.chojo.repbot.data.GuildData;
import de.chojo.repbot.data.ReputationData;
import de.chojo.repbot.data.wrapper.ReputationRole;
import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.util.Objects;

public class RoleAssigner {
    private final GuildData guildData;
    private final ReputationData reputationData;

    public RoleAssigner(DataSource dataSource) {
        guildData = new GuildData(dataSource);
        reputationData = new ReputationData(dataSource);
    }

    public void update(@Nullable Member member) {
        if (member == null) return;
        var guild = member.getGuild();
        var reputation = reputationData.getReputation(guild, member.getUser()).orElse(0);

        var optRepRole = guildData.getCurrentReputationRole(guild, reputation);

        if (optRepRole.isEmpty()) return;

        var repRole = optRepRole.get();

        var roleById = guild.getRoleById(repRole.getRoleId());

        if (roleById == null) return;

        if (member.getRoles().contains(roleById)) return;

        cleanMemberRoles(member);

        guild.addRoleToMember(member, roleById).queue();
    }

    private void cleanMemberRoles(Member member) {
        var guild = member.getGuild();
        guildData.getReputationRoles(guild)
                .stream()
                .map(ReputationRole::getRoleId)
                .map(guild::getRoleById)
                .filter(Objects::nonNull)
                .forEach(r -> guild.removeRoleFromMember(member, r).queue());
    }
}
