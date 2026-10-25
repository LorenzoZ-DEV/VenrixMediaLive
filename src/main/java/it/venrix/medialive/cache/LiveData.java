package it.venrix.medialive.cache;

/**
 * Stato live di un singolo giocatore tenuto in memoria.
 * <p>
 * I campi sono volatile perche' l'oggetto viene letto/scritto da thread diversi
 * (main thread, executor del database, thread interni di TAB).
 */
public final class LiveData {

    private volatile boolean live;
    private volatile String url;

    public LiveData(boolean live, String url) {
        this.live = live;
        this.url = url;
    }

    /** Stato di default per un giocatore mai visto prima. */
    public static LiveData empty() {
        return new LiveData(false, null);
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    /** Link della live, oppure {@code null} se non ne ha mai pubblicato uno. */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
