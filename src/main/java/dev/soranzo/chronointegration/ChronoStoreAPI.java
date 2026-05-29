package dev.soranzo.chronointegration;

import java.util.List;

public interface ChronoStoreAPI {
    List<PlayerRecord> getPlayers();
    ConfigRecord getChronoConfig();
    List<SessionRecord> getSessions();
    List<PlayerRanking> getTopPlayers(int limit);
}
