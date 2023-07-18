package fr.theorozier.webstreamer.display.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.theorozier.webstreamer.WebStreamer;
import fr.theorozier.webstreamer.WebStreamerClient;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.DisplayMenu;
import fr.theorozier.webstreamer.display.DisplayNetworking;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.display.source.RawDisplaySource;
import fr.theorozier.webstreamer.display.source.TwitchDisplaySource;
import fr.theorozier.webstreamer.playlist.Playlist;
import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import fr.theorozier.webstreamer.util.AsyncProcessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class DisplayScreen extends Screen implements MenuAccess<DisplayMenu> {

    private static final Component CONF_TEXT = Component.translatable("gui.webstreamer.display.conf");
    private static final Component WIDTH_TEXT = Component.translatable("gui.webstreamer.display.width");
    private static final Component HEIGHT_TEXT = Component.translatable("gui.webstreamer.display.height");
    private static final Component SOURCE_TYPE_TEXT = Component.translatable("gui.webstreamer.display.sourceType");
    private static final Component SOURCE_TYPE_RAW_TEXT = Component.translatable("gui.webstreamer.display.sourceType.raw");
    private static final Component SOURCE_TYPE_TWITCH_TEXT = Component.translatable("gui.webstreamer.display.sourceType.twitch");
    private static final Component URL_TEXT = Component.translatable("gui.webstreamer.display.url");
    private static final Component CHANNEL_TEXT = Component.translatable("gui.webstreamer.display.channel");
    private static final Component MALFORMED_URL_TEXT = Component.translatable("gui.webstreamer.display.malformedUrl");
    private static final Component NO_QUALITY_TEXT = Component.translatable("gui.webstreamer.display.noQuality");
    private static final Component QUALITY_TEXT = Component.translatable("gui.webstreamer.display.quality");
    private static final String AUDIO_DISTANCE_TEXT_KEY = "gui.webstreamer.display.audioDistance";
    private static final String AUDIO_VOLUME_TEXT_KEY = "gui.webstreamer.display.audioVolume";

    private static final Component ERR_NO_TOKEN_TEXT = Component.translatable("gui.webstreamer.display.error.noToken");
    private static final Component ERR_CHANNEL_NOT_FOUND_TEXT = Component.translatable("gui.webstreamer.display.error.channelNotFound");
    private static final Component ERR_CHANNEL_OFFLINE_TEXT = Component.translatable("gui.webstreamer.display.error.channelOffline");
    private static final String ERR_UNKNOWN_TEXT_KEY = "gui.webstreamer.display.error.unknown";

    private final DisplayMenu menu;
    private final DisplayBlockEntity blockEntity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SourceScreen<?> sourceScreen;
    private SourceType sourceType;

    private int xHalf;
    private int yTop;
    private int ySourceTop;

    private EditBox displayWidthField;
    private EditBox displayHeightField;
    private Button doneButton;

    private float displayWidth;
    private float displayHeight;
    private float displayAudioDistance;
    private float displayAudioVolume;

    public DisplayScreen(DisplayMenu menu, Inventory inventory, Component title) {
        super(title);

        this.menu = menu;

        assert Minecraft.getInstance().level != null;
        this.blockEntity = Minecraft.getInstance().level.getBlockEntity(menu.getPos(), WebStreamer.DISPLAY_BLOCK_ENTITY).orElse(null);
        if (blockEntity == null) {
            WebStreamer.LOGGER.warn("DisplayBlockEntity not found at {}", menu.getPos());
            onClose();
            return;
        }

        DisplaySource source = blockEntity.getSource();
        if (source instanceof RawDisplaySource rawSource) {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen(new RawDisplaySource(rawSource));
        } else if (source instanceof TwitchDisplaySource twitchSource) {
            this.sourceType = SourceType.TWITCH;
            this.sourceScreen = new TwitchSourceScreen(new TwitchDisplaySource(twitchSource));
        } else {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen();
        }

        this.displayWidth = blockEntity.getWidth();
        this.displayHeight = blockEntity.getHeight();
        this.displayAudioDistance = blockEntity.getAudioDistance();
        this.displayAudioVolume = blockEntity.getAudioVolume();

    }

    @NotNull
    @Override
    public DisplayMenu getMenu() {
        return menu;
    }

    private void setSourceScreen(SourceScreen<?> sourceScreen) {
        this.sourceScreen = sourceScreen;
        if (this.minecraft != null) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    @Override
    protected void init() {

        this.xHalf = this.width / 2;
        this.yTop = 60;
        this.ySourceTop = 130;

        String displayWidthRaw = this.displayWidthField == null ? Float.toString(this.displayWidth) : this.displayWidthField.getValue();
        String displayHeightRaw = this.displayHeightField == null ? Float.toString(this.displayHeight) : this.displayHeightField.getValue();

        this.displayWidthField = new EditBox(this.font, xHalf - 154, yTop + 11, 50, 18, Component.empty());
        this.displayWidthField.setValue(displayWidthRaw);
        this.displayWidthField.setResponder(this::onDisplayWidthChanged);
        this.addRenderableWidget(this.displayWidthField);
        this.addWidget(this.displayWidthField);

        this.displayHeightField = new EditBox(this.font, xHalf - 96, yTop + 11, 50, 18, Component.empty());
        this.displayHeightField.setValue(displayHeightRaw);
        this.displayHeightField.setResponder(this::onDisplayHeightChanged);
        this.addRenderableWidget(this.displayHeightField);
        this.addWidget(this.displayHeightField);

        CycleButton<SourceType> sourceTypeButton = CycleButton.builder(SourceType::getText)
            .withValues(SourceType.values())
            .create(xHalf - 38, yTop + 10, 192, 20, SOURCE_TYPE_TEXT, this::onSourceTypeChanged);

        sourceTypeButton.setValue(this.sourceType);
        this.addRenderableWidget(sourceTypeButton);

        AudioDistanceSliderWidget audioDistanceSlider = new AudioDistanceSliderWidget(xHalf - 154, yTop + 36, 150, 20, this.displayAudioDistance, 64);
        audioDistanceSlider.setChangedListener(dist -> this.displayAudioDistance = dist);
        this.addRenderableWidget(audioDistanceSlider);

        AudioVolumeSliderWidget audioVolumeSlider = new AudioVolumeSliderWidget(xHalf + 4, yTop + 36, 150, 20, this.displayAudioVolume);
        audioVolumeSlider.setChangedListener(volume -> this.displayAudioVolume = volume);
        this.addRenderableWidget(audioVolumeSlider);

        this.doneButton = new Button(
            xHalf - 4 - 150, height / 4 + 120 + 12, 150, 20, CommonComponents.GUI_DONE,
            button -> this.commitAndClose()
        );
        this.addRenderableWidget(this.doneButton);

        Button cancelButton = new Button(
            xHalf + 4, height / 4 + 120 + 12, 150, 20, CommonComponents.GUI_CANCEL,
            button -> this.onClose()
        );
        this.addRenderableWidget(cancelButton);

        if (this.sourceScreen != null) {
            this.sourceScreen.init();
        }

    }

    private void onDisplayWidthChanged(String widthRaw) {
        try {
            this.displayWidth = Float.parseFloat(widthRaw);
        } catch (NumberFormatException e) {
            this.displayWidth = Float.NaN;
        }
    }

    private void onDisplayHeightChanged(String heightRaw) {
        try {
            this.displayHeight = Float.parseFloat(heightRaw);
        } catch (NumberFormatException e) {
            this.displayHeight = Float.NaN;
        }
    }

    private void onSourceTypeChanged(CycleButton<SourceType> button, SourceType type) {
        if (type != this.sourceType) {
            this.sourceType = type;
            this.setSourceScreen(switch (type) {
                case RAW -> new RawSourceScreen();
                case TWITCH -> new TwitchSourceScreen();
            });
        }
    }

    private void commitAndClose() {
        if (Float.isFinite(this.displayWidth) && Float.isFinite(this.displayHeight)) {
            this.blockEntity.setSize(this.displayWidth, this.displayHeight);
            this.blockEntity.setAudioConfig(this.displayAudioDistance, this.displayAudioVolume);
            if (this.sourceScreen != null) {
                this.blockEntity.setSource(this.sourceScreen.source);
            }
            DisplayNetworking.sendDisplayUpdate(this.blockEntity);
        }
        this.onClose();
    }

    @Override
    public void render(@NotNull PoseStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredString(matrices, this.font, CONF_TEXT, xHalf, 20, 0xFFFFFF);
        drawString(matrices, this.font, WIDTH_TEXT, xHalf - 154, yTop + 1, 0xA0A0A0);
        drawString(matrices, this.font, HEIGHT_TEXT, xHalf - 96, yTop + 1, 0xA0A0A0);
        if (this.sourceScreen != null) {
            this.sourceScreen.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        this.displayWidthField.tick();
        this.displayHeightField.tick();
        this.doneButton.active = Float.isFinite(this.displayWidth) && Float.isFinite(this.displayHeight);
        if (this.sourceScreen != null) {
            this.sourceScreen.tick();
            if (this.doneButton.active && !this.sourceScreen.valid()) {
                this.doneButton.active = false;
            }
        }
    }

    private enum SourceType {

        RAW(SOURCE_TYPE_RAW_TEXT),
        TWITCH(SOURCE_TYPE_TWITCH_TEXT);

        private final Component text;
        SourceType(Component text) {
            this.text = text;
        }

        public Component getText() {
            return text;
        }

    }

    /**
     * A basic source screen.
     */
    private abstract static class SourceScreen<S extends DisplaySource> implements Widget {

        protected final S source;

        SourceScreen(S source) {
            this.source = source;
        }

        abstract boolean valid();
        abstract void init();
        abstract void tick();

    }

    /**
     * Screen for raw sources.
     */
    private class RawSourceScreen extends SourceScreen<RawDisplaySource> {

        private EditBox urlField;

        private final AsyncProcessor<String, URI, IllegalArgumentException> asyncUrl = new AsyncProcessor<>(URI::create);

        RawSourceScreen(RawDisplaySource source) {
            super(source);
        }

        RawSourceScreen() {
            this(new RawDisplaySource());
        }

        @Override
        public boolean valid() {
            return this.asyncUrl.idle();
        }

        @Override
        public void init() {

            boolean first = (this.urlField == null);

            this.urlField = new EditBox(font, xHalf - 154, ySourceTop + 10, 308, 20, this.urlField, Component.empty());
            this.urlField.setMaxLength(32000);
            this.urlField.setResponder(this::onUrlChanged);
            addWidget(this.urlField);
            setInitialFocus(this.urlField);
            addRenderableWidget(this.urlField);

            if (first) {
                this.urlField.setValue(Objects.toString(this.source.getUri(), ""));
            }

        }

        @Override
        public void render(@NotNull PoseStack matrices, int mouseX, int mouseY, float delta) {
            drawString(matrices, font, URL_TEXT, xHalf - 154, ySourceTop, 0xA0A0A0);
            if (this.source.getUri() == null) {
                drawCenteredString(matrices, font, MALFORMED_URL_TEXT, xHalf, ySourceTop + 50, 0xFF6052);
            }
        }

        @Override
        public void tick() {
            this.urlField.tick();
            this.asyncUrl.fetch(executor, this.source::setUri, exc -> this.source.setUri(null));
        }

        private void onUrlChanged(String rawUrl) {
            this.asyncUrl.push(rawUrl);
        }

    }

    private class TwitchSourceScreen extends SourceScreen<TwitchDisplaySource> {

        private EditBox channelField;
        private QualitySliderWidget qualitySlider;

        private String firstQuality;

        private final AsyncProcessor<String, Playlist, TwitchClient.PlaylistException> asyncPlaylist;
        private Playlist playlist;
        private Component playlistError;

        TwitchSourceScreen(TwitchDisplaySource source) {
            super(source);
            this.firstQuality = source.getQuality();
            this.asyncPlaylist = new AsyncProcessor<>(WebStreamerClient.TWITCH_CLIENT::requestPlaylist);
        }

        TwitchSourceScreen() {
            this(new TwitchDisplaySource());
        }

        @Override
        public boolean valid() {
            return this.asyncPlaylist.idle();
        }

        @Override
        public void init() {

            boolean first = (this.channelField == null);

            this.channelField = new EditBox(font, xHalf - 154, ySourceTop + 10, 308, 20, this.channelField, Component.empty());
            this.channelField.setMaxLength(64);
            this.channelField.setResponder(this::onChannelChanged);
            addWidget(this.channelField);
            setInitialFocus(this.channelField);
            addRenderableWidget(this.channelField);

            this.qualitySlider = new QualitySliderWidget(xHalf - 154, ySourceTop + 50, 308, 20, this.qualitySlider);
            this.qualitySlider.setChangedListener(this::onQualityChanged);
            this.updateQualitySlider();
            addWidget(this.qualitySlider);
            addRenderableWidget(this.qualitySlider);

            if (first) {
                this.channelField.setValue(Objects.toString(this.source.getChannel(), ""));
            }

        }

        @Override
        public void render(@NotNull PoseStack matrices, int mouseX, int mouseY, float delta) {
            drawString(matrices, font, CHANNEL_TEXT, xHalf - 154, ySourceTop, 0xA0A0A0);
            if (this.playlistError == null) {
                drawString(matrices, font, QUALITY_TEXT, xHalf - 154, ySourceTop + 40, 0xA0A0A0);
            } else {
                drawCenteredString(matrices, font, this.playlistError, xHalf, ySourceTop + 50, 0xFF6052);
            }
        }

        @Override
        public void tick() {

            this.channelField.tick();

            this.asyncPlaylist.fetch(executor, pl -> {
                this.playlist = pl;
                this.qualitySlider.setQualities(pl.getQualities());
                if (this.firstQuality != null) {
                    this.qualitySlider.setQuality(this.firstQuality);
                    this.firstQuality = null;
                }
                this.playlistError = null;
                this.updateQualitySlider();
            }, exc -> {
                this.playlist = null;
                this.qualitySlider.setQualities(null);
                this.playlistError = switch (exc.getExceptionType()) {
                    case UNKNOWN -> Component.translatable(ERR_UNKNOWN_TEXT_KEY, "");
                    case NO_TOKEN -> ERR_NO_TOKEN_TEXT;
                    case CHANNEL_NOT_FOUND -> ERR_CHANNEL_NOT_FOUND_TEXT;
                    case CHANNEL_OFFLINE -> ERR_CHANNEL_OFFLINE_TEXT;
                };
                this.updateQualitySlider();
            });

        }

        private void onChannelChanged(String channel) {
            this.asyncPlaylist.push(channel);
        }

        private void onQualityChanged(PlaylistQuality quality) {
            if (quality == null) {
                this.source.clearChannelQuality();
            } else if (this.playlist != null) {
                this.source.setChannelQuality(this.playlist.getChannel(), quality.name());
            }
        }

        private void updateQualitySlider() {
            this.qualitySlider.visible = (this.playlistError == null);
        }

    }

    private static class QualitySliderWidget extends AbstractSliderButton {

        private int qualityIndex = -1;
        private List<PlaylistQuality> qualities;
        private Consumer<PlaylistQuality> changedListener;

        public QualitySliderWidget(int x, int y, int width, int height, QualitySliderWidget previousSlider) {
            super(x, y, width, height, Component.empty(), 0.0);
            if (previousSlider != null && previousSlider.qualities != null) {
                this.setQualities(previousSlider.qualities);
                this.qualityIndex = previousSlider.qualityIndex;
                this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                this.updateMessage();
            } else {
                this.setQualities(null);
            }
        }

        public void setQualities(List<PlaylistQuality> qualities) {
            this.qualities = qualities;
            this.applyValue();
            this.updateMessage();
        }

        public void setQuality(String quality) {
            for (int i = 0; i < this.qualities.size(); i++) {
                if (this.qualities.get(i).name().equals(quality)) {
                    this.qualityIndex = i;
                    this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                    this.updateMessage();
                    if (this.changedListener != null) {
                        this.changedListener.accept(this.qualities.get(i));
                    }
                    return;
                }
            }
        }

        public void setChangedListener(Consumer<PlaylistQuality> changedListener) {
            this.changedListener = changedListener;
        }

        @Override
        protected void updateMessage() {
            if (this.qualityIndex < 0) {
                this.setMessage(NO_QUALITY_TEXT);
            } else {
                this.setMessage(Component.literal(this.qualities.get(this.qualityIndex).name()));
            }
        }

        @Override
        protected void applyValue() {
            if (this.qualities == null || this.qualities.isEmpty()) {
                this.value = 0.0;
                this.qualityIndex = -1;
                this.active = false;
                if (this.changedListener != null) {
                    this.changedListener.accept(null);
                }
            } else {
                this.qualityIndex = (int) Math.round(this.value * (this.qualities.size() - 1));
                this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                this.active = true;
                if (this.changedListener != null) {
                    this.changedListener.accept(this.qualities.get(this.qualityIndex));
                }
            }
        }

    }

    private static class AudioDistanceSliderWidget extends AbstractSliderButton {

        private final float maxDistance;
        private Consumer<Float> changedListener;

        public AudioDistanceSliderWidget(int x, int y, int width, int height, float distance, float maxDistance) {
            super(x, y, width, height, Component.empty(), distance / maxDistance);
            this.maxDistance = maxDistance;
            this.updateMessage();
        }

        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }

        private float getDistance() {
            return (float) (this.value * this.maxDistance);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(AUDIO_DISTANCE_TEXT_KEY).append(": ").append(Integer.toString((int) this.getDistance())));
        }

        @Override
        protected void applyValue() {
            this.changedListener.accept(this.getDistance());
        }

    }

    private static class AudioVolumeSliderWidget extends AbstractSliderButton {

        private Consumer<Float> changedListener;

        public AudioVolumeSliderWidget(int x, int y, int width, int height, float value) {
            super(x, y, width, height, Component.empty(), value);
            this.updateMessage();
        }

        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }

        @Override
        protected void updateMessage() {
            Component text = (this.value == this.getYImage(false)) ? CommonComponents.OPTION_OFF : Component.literal((int)(this.value * 100.0) + "%");
            this.setMessage(Component.translatable(AUDIO_VOLUME_TEXT_KEY).append(": ").append(text));
        }

        @Override
        protected void applyValue() {
            this.changedListener.accept((float) this.value);
        }

    }

}
