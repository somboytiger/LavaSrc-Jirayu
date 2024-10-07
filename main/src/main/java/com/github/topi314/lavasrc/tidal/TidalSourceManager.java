package com.github.topi314.lavasrc.tidal;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class TidalSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("^(https?://)?(www\\.)?(listen\\.)?(embed\\.)?tidal\\.com/((browse/)?(?<type>track|album|playlist|mix|artist)|(tracks|albums|playlists|artists))/(?<identifier>[a-zA-Z0-9-_]+)");
	public static final String SEARCH_PREFIX = "tdsearch:";
	public static final String API_BASE = "https://api.tidal.com/v1/";
	private static final Logger log = LoggerFactory.getLogger(TidalSourceManager.class);
	private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	private final String tidalToken;
	private final String countryCode;
	private int tracksSearchLimit = 50;
	private int playlistTracksLoadLimit = 100;
	private int artistTopTracksLoadLimit = 100;

	public TidalSourceManager(String[] providers, String tidalToken, String countryCode, AudioPlayerManager audioPlayerManager) {
		this(tidalToken, countryCode, unused -> audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public TidalSourceManager(String[] providers, String tidalToken, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager) {
		this(tidalToken, countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public TidalSourceManager(String tidalToken, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		this(tidalToken, countryCode, unused -> audioPlayerManager, mirroringAudioTrackResolver);
	}

	public TidalSourceManager(String tidalToken, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);

		this.tidalToken = tidalToken;
		if (countryCode == null || countryCode.isEmpty()) countryCode = "US";
		this.countryCode = countryCode;
	}

	public void setTracksSearchLimit(int tracksSearchLimit) {
		this.tracksSearchLimit = tracksSearchLimit;
	}

	public void setPlaylistTracksLoadLimit(int playlistTracksLoadLimit) {
		this.playlistTracksLoadLimit = playlistTracksLoadLimit;
	}

	public void setArtistTopTracksLoadLimit(int artistTopTracksLoadLimit) {
		this.artistTopTracksLoadLimit = artistTopTracksLoadLimit;
	}

	private JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.addHeader("x-tidal-token", this.tidalToken);
		return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "tidal";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new TidalAudioTrack(trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
			extendedAudioTrackInfo.isPreview,
			this
		);
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			if (reference.identifier.startsWith(SEARCH_PREFIX)) return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());

			var matcher = URL_PATTERN.matcher(reference.identifier);

			if (!matcher.find()) return null;

			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "album":
					return this.getAlbum(id);

				case "playlist":
					return this.getPlaylist(id);

				case "mix":
					return this.getMix(id);

				case "track":
					return this.getTrack(id);

				case "artist":
					return this.getArtist(id);
			}
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		return null;
	}

	public AudioItem getAlbum(String id) throws IOException {
		var json = this.getJson(API_BASE + "albums/" + id + "/tracks?" + "countryCode=" + this.countryCode);
		if (json == null || json.get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new TidalAudioPlaylist(json.get("items").index(0).get("album").get("title").text(), this.parseTracks(json), ExtendedAudioPlaylist.Type.ALBUM, "https://tidal.com/browse/album/" + id, null, null, Integer.valueOf(json.get("totalNumberOfItems").text()));
	}

	public AudioItem getPlaylist(String id) throws IOException {
		var playlistInfoJson = this.getJson(API_BASE + "playlists/" + id + "?&countryCode=" + this.countryCode);
		if (playlistInfoJson == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksJson = this.getJson(API_BASE + "playlists/" + id + "/tracks?" + "&countryCode=" + this.countryCode + "&limit=" + this.playlistTracksLoadLimit + "&offset=0");
		if (tracksJson == null || tracksJson.get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new TidalAudioPlaylist(playlistInfoJson.get("title").text(), this.parseTracks(tracksJson), ExtendedAudioPlaylist.Type.PLAYLIST, "https://tidal.com/browse/playlist/" + id, null, null, Integer.valueOf(playlistInfoJson.get("numberOfTracks").text()));
	}

	public AudioItem getMix(String id) throws IOException {
		var json = this.getJson(API_BASE + "mixes/" + id + "/items" + "?countryCode=" + this.countryCode);
		if (json == null || json.get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new TidalAudioPlaylist("Mix", this.parseMixTracks(json), ExtendedAudioPlaylist.Type.PLAYLIST, "https://tidal.com/browse/mix/" + id, null, null, Integer.valueOf(json.get("numberOfTracks").text()));
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(API_BASE + "search/tracks?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + this.tracksSearchLimit + "&offset=0&countryCode=" + this.countryCode);

		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json);
		return new BasicAudioPlaylist("Tidal search results for: " + query, tracks, null, true);
	}

	private AudioItem getTrack(String id) throws IOException {
		var json = this.getJson(API_BASE + "tracks/" + id + "?countryCode=" + this.countryCode);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json);
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson(API_BASE + "artists/" + id + "/toptracks" + "?limit=" + this.artistTopTracksLoadLimit + "&offset=0" + "&countryCode=" + this.countryCode);
		if (json == null || json.get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var author = json.get("items").index(0).get("artist").get("name").text();
		return new TidalAudioPlaylist(author + " - Top tracks", this.parseTracks(json), ExtendedAudioPlaylist.Type.ARTIST, "https://tidal.com/browse/artist/" + id, null, null, Integer.valueOf(json.get("totalNumberOfItems").text()));
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("items").values()) {
			tracks.add(this.parseTrack(value));
		}
		return tracks;
	}

	private List<AudioTrack> parseMixTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var item : json.get("items").values()) {
			var trackItem = item.get("item");
			tracks.add(this.parseTrack(trackItem));
		}
		return tracks;
	}

	private String parseArtworkUrl(JsonBrowser json) {
		var text = json.get("album").get("cover").text();
		if (text == null) {
			return null;
		}
		return text.replace("-", "/");
	}

	private AudioTrack parseTrack(JsonBrowser json) {

		return new TidalAudioTrack(
			new AudioTrackInfo(
				json.get("title").text(),
				json.get("artist").get("name").text(),
				json.get("duration").as(Long.class) * 1000,
				json.get("id").text(),
				false,
				json.get("url").text(),
				"https://resources.tidal.com/images/" + parseArtworkUrl(json) + "/1080x1080.jpg",
				json.get("isrc").text()
			),
			json.get("album").get("title").text(),
			"https://tidal.com/browse/album/" + json.get("album").get("id").text(),
			"https://tidal.com/browse/artist/" + json.get("artist").get("id").text(),
			null,
			null,
			false,
			this
		);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.httpInterfaceManager.configureBuilder(configurator);
	}
}