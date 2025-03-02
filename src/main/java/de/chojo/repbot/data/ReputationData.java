package de.chojo.repbot.data;

import de.chojo.repbot.data.util.DbUtil;
import de.chojo.repbot.data.wrapper.ReputationUser;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Slf4j
public class ReputationData {
    private final DataSource source;

    public ReputationData(DataSource source) {
        this.source = source;
    }

    public boolean logReputation(Guild guild, User donor, User receiver, Message message) {
        try (var conn = source.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO
                        reputation_log(guild_id, donor_id, receiver_id, message_id) VALUES(?,?,?,?)
                            ON CONFLICT(guild_id, donor_id, receiver_id, message_id)
                                DO NOTHING;
                                        """)) {
                stmt.setLong(1, guild.getIdLong());
                stmt.setLong(2, donor.getIdLong());
                stmt.setLong(3, receiver.getIdLong());
                stmt.setLong(4, message.getIdLong());
                stmt.execute();
                return true;
            }
        } catch (SQLException e) {
            DbUtil.logSQLError("Could not log reputation", e);
            return false;
        }
    }

    public Optional<Instant> getLastRated(Guild guild, User donor, User receiver) {
        try (var conn = source.getConnection(); var stmt = conn.prepareStatement("""
                                SELECT
                                    received
                                from
                                    reputation_log
                                where
                                    guild_id = ?
                                    AND donor_id = ?
                                    AND receiver_id = ?;
                """)) {
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, donor.getIdLong());
            stmt.setLong(3, receiver.getIdLong());
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getTimestamp("received").toInstant());
            }
        } catch (SQLException e) {
            DbUtil.logSQLError("Could not get last rated.", e);
        }
        return Optional.empty();
    }

    public List<ReputationUser> getRanking(Guild guild, int limit, int offset) {
        try (var conn = source.getConnection(); var stmt = conn.prepareStatement("""
                SELECT
                    user_id,
                    reputation
                from
                    user_reputation
                WHERE guild_id = ?
                ORDER BY reputation DESC
                OFFSET ?
                LIMIT ?
                """)) {
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, offset);
            stmt.setLong(3, limit);
            var rs = stmt.executeQuery();
            var users = new ArrayList<ReputationUser>();
            while (rs.next()) {
                users.add(
                        new ReputationUser(
                                rs.getLong("user_id"),
                                rs.getLong("reputation")
                        )
                );
            }
            return users;
        } catch (SQLException e) {
            DbUtil.logSQLError("Could not retrieve user ranking", e);
        }
        return Collections.emptyList();
    }

    public OptionalLong getReputation(Guild guild, User user) {
        try (var conn = source.getConnection(); var stmt = conn.prepareStatement("""
                SELECT reputation from user_reputation where guild_id = ? and user_id = ?
                """)) {
            stmt.setLong(1, guild.getIdLong());
            stmt.setLong(2, user.getIdLong());
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return OptionalLong.of(rs.getLong("reputation"));
            }
        } catch (SQLException e) {
            DbUtil.logSQLError("Could not retrieve user reputation", e);
        }
        return OptionalLong.empty();
    }

    public void removeMessage(long messageId) {
        try (var conn = source.getConnection(); var stmt = conn.prepareStatement("""
                DELETE FROM reputation_log where message_id = ?;
                """)) {
            stmt.setLong(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            DbUtil.logSQLError("Could not delete message from log", e);
        }
    }

    public Long getLastRatedDuration(Guild guild, User donor, User receiver, ChronoUnit unit) {
        return getLastRated(guild, donor, receiver).map(i -> i.until(Instant.now(), unit)).orElse(Long.MAX_VALUE);
    }
}
