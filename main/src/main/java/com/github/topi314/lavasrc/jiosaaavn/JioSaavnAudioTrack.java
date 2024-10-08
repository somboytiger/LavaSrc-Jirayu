package com.github.topi314.lavasrc.jiosaaavn;


import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class JioSaavnAudioTrack extends ExtendedAudioTrack {
	protected static final Logger log = LoggerFactory.getLogger(JioSaavnAudioTrack.class);

	private final JioSavaanSourceManager sourceManager;

	public JioSaavnAudioTrack(AudioTrackInfo trackInfo, JioSavaanSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public JioSaavnAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, JioSavaanSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);

		this.sourceManager = sourceManager;
	}

	protected HttpInterface getHttpInterface() {
		return this.sourceManager.getInterface();
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (HttpInterface httpInterface = getHttpInterface()) {
			loadStream(executor, httpInterface);
		}
	}

	protected void loadStream(LocalAudioTrackExecutor localExecutor, HttpInterface httpInterface) throws Exception {
		final String trackUrl = getPlaybackUrl();
		try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackUrl), this.getTrackDuration())) {
			processDelegate(createAudioTrack(this.trackInfo, stream), localExecutor);
		} catch (Exception e) {
			log.error("Failed to load track from URL: {}", trackUrl, e);
			throw e;
		}
	}

	protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
		return new MpegAudioTrack(trackInfo, stream);
	}

	public String getPlaybackUrl() {
		return getDownloadURL(this.trackInfo.identifier);
	}

	private String getDownloadURL(String identifier) {
		// Fetch JSON data from the API
		var json = fetchJson(JioSavaanSourceManager.BASE_API + "/songs?ids=" + identifier, this.sourceManager);

		// Extract the download URL information
		var downloadInfoLink = json.get("data").index(0).get("downloadUrl");

		if (downloadInfoLink.isNull()) {
			return null;
		}

		// Retrieve all download URLs
		var downloadUrls = downloadInfoLink.values();
		if (downloadUrls.isEmpty()) {
			return null;
		}

		String downloadUrl = null;

		// Check for the desired quality in descending order
		for (var url : downloadUrls) {
			if (url.get("quality").text().equals("320kbps")) {
				downloadUrl = url.get("url").text();
				break;
			}
		}
		if (downloadUrl == null) {
			for (var url : downloadUrls) {
				if (url.get("quality").text().equals("160kbps")) {
					downloadUrl = url.get("url").text();
					break;
				}
			}
		}
		if (downloadUrl == null) {
			for (var url : downloadUrls) {
				if (url.get("quality").text().equals("96kbps")) {
					downloadUrl = url.get("url").text();
					break;
				}
			}
		}
		if (downloadUrl == null && !downloadUrls.isEmpty()) {
			downloadUrl = downloadUrls.get(0).get("url").text();
		}

		return downloadUrl;
	}

	protected long getTrackDuration() {
		return Units.CONTENT_LENGTH_UNKNOWN;
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new JioSaavnAudioTrack(this.trackInfo, this.sourceManager);
	}

	public static JsonBrowser fetchJson(String pageURl, JioSavaanSourceManager sourceManager) {
		final HttpGet httpGet = new HttpGet(pageURl);
		try (final CloseableHttpResponse response = sourceManager.getInterface().execute(httpGet)) {
			final String content = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			return JsonBrowser.parse(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
