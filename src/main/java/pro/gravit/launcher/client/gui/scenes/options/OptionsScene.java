package pro.gravit.launcher.client.gui.scenes.options;

import animatefx.animation.FadeIn;
import animatefx.animation.SlideInLeft;
import animatefx.animation.SlideInUp;
import com.google.gson.reflect.TypeToken;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.scenes.AbstractScene;
import pro.gravit.launcher.client.gui.scenes.servermenu.ServerMenuScene;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalFile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class OptionsScene extends AbstractScene {
    private Pane componentList;
    private OptionalView optionalView;
    private ImageView avatar;
    private Image originalAvatarImage;
    ClientProfile profile;
    private FlowPane flowPane;
    public OptionsScene(JavaFXApplication application) {
        super("scenes/options/options.fxml", application);
    }

    @Override
    protected void doInit() {
        flowPane = (FlowPane) LookupHelper.<ScrollPane>lookup(layout, "#optionslist").getContent();
        avatar = LookupHelper.lookup(layout, "#avatar");
        originalAvatarImage = avatar.getImage();
        LookupHelper.<ImageView>lookupIfPossible(layout, "#avatar").ifPresent(
                (h) -> {
                    try {
                        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(h.getFitWidth(), h.getFitHeight());
                        clip.setArcWidth(h.getFitWidth());
                        clip.setArcHeight(h.getFitHeight());
                        h.setClip(clip);
                        h.setImage(originalAvatarImage);
                    } catch (Throwable e) {
                        LogHelper.warning("Skin head error");
                    }
                }
        );
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#optionslist").getContent();
        LookupHelper.<Button>lookupIfPossible(header, "#back").ifPresent(x -> x.setOnAction((e) -> {
            try {
                application.stateService.setOptionalView(profile, optionalView);
                switchScene(application.gui.serverInfoScene);
                application.gui.serverInfoScene.reset();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
        LookupHelper.<Button>lookupIfPossible(layout, "#settings").ifPresent(x -> x.setOnAction((e) -> {
            try {
                application.stateService.setOptionalView(profile, optionalView);
                switchScene(application.gui.settingsScene);
                application.gui.settingsScene.reset();
            } catch (Exception exception) {
                errorHandle(exception);
            }
        }));
    }

    @Override
    public void reset() {
        new SlideInUp(LookupHelper.lookup(layout, "#content")).play();
        new FadeIn(LookupHelper.lookup(layout, "#leftPane")).play();
        profile = application.stateService.getProfile();
        LookupHelper.<Label>lookup(layout, "#serverName").setText(profile.getTitle());
        avatar.setImage(originalAvatarImage);
        LookupHelper.<Label>lookupIfPossible(layout, "#nickname").ifPresent((e) -> e.setText(application.stateService.getUsername()));
        ServerMenuScene.putAvatarToImageView(application, application.stateService.getUsername(), avatar);
        componentList.getChildren().clear();

    }

    @Override
    public String getName() {
        return "options";
    }

    private final Map<OptionalFile, Consumer<Boolean>> watchers = new HashMap<>();

    private void callWatcher(OptionalFile file, Boolean value) {
        for (Map.Entry<OptionalFile, Consumer<Boolean>> v : watchers.entrySet()) {
            if (v.getKey() == file) {
                v.getValue().accept(value);
                break;
            }
        }
    }

    public void addProfileOptionals(OptionalView view) {
        this.optionalView = new OptionalView(view);
        watchers.clear();
        for (OptionalFile optionalFile : optionalView.all) {
            if (!optionalFile.visible)
                continue;

            Consumer<Boolean> setCheckBox = add(optionalFile.name, optionalFile.info, optionalView.enabled.contains(optionalFile),
                    optionalFile.subTreeLevel, (isSelected) -> {
                        if (isSelected)
                            optionalView.enable(optionalFile, true, this::callWatcher);
                        else
                            optionalView.disable(optionalFile, this::callWatcher);
                    });
            watchers.put(optionalFile, setCheckBox);
        }
    }

    public Consumer<Boolean> add(String name, String description, boolean value, int padding, Consumer<Boolean> onChanged) {
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        Pane mainPane = new Pane();
        CheckBox checkBox = new CheckBox();
        Pane pane = new Pane();
        pane.setMinWidth(200);
        pane.setMaxWidth(200);
        pane.setMinHeight(88);
        pane.setMaxHeight(88);
        Label label = new Label();
        Label maintext = new Label();
        mainPane.getChildren().add(checkBox);
        pane.getChildren().add(label);
        pane.getChildren().add(maintext);
        checkBox.setGraphic(pane);
        mainPane.getStyleClass().add("optional-container");
        checkBox.setSelected(value);
        checkBox.setCursor(Cursor.HAND);
        checkBox.setMinWidth(200);
        checkBox.setMaxWidth(200);
        checkBox.setMinHeight(88);
        checkBox.setMaxHeight(88);
        checkBox.setOnAction((e) -> {
            onChanged.accept(checkBox.isSelected());
            application.stateService.setOptionalView(profile, optionalView);
        });
        checkBox.getStyleClass().add("optional-checkbox");
        maintext.setText(name);
        maintext.setWrapText(true);
        maintext.setPrefWidth(180);
        mainPane.setOpacity(0.5);
        maintext.getStyleClass().add("maintext");
        label.setText(description);
        label.setPrefWidth(180);
        label.setWrapText(true);
        label.getStyleClass().add("descriptiontext");
        componentList.getChildren().add(mainPane);
        return checkBox::setSelected;
    }

    public void saveAll() throws IOException {
        List<ClientProfile> profiles = application.stateService.getProfiles();
        Map<ClientProfile, OptionalView> optionalViewMap = application.stateService.getOptionalViewMap();
        if (profiles == null)
            return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        List<OptionalListEntry> list = new ArrayList<>(5);
        for (ClientProfile clientProfile : profiles) {
            OptionalListEntry entry = new OptionalListEntry();
            entry.name = clientProfile.getTitle();
            entry.profileUUID = clientProfile.getUUID();
            OptionalView view = optionalViewMap.get(clientProfile);
            view.all.forEach((optionalFile -> {
                if (optionalFile.visible) {
                    boolean isEnabled = view.enabled.contains(optionalFile);
                    OptionalView.OptionalFileInstallInfo installInfo = view.installInfo.get(optionalFile);
                    entry.enabled.add(new OptionalListEntryPair(optionalFile, isEnabled, installInfo));
                }
            }));
            list.add(entry);
        }
        try (Writer writer = IOHelper.newWriter(optionsFile)) {
            Launcher.gsonManager.gson.toJson(list, writer);
        }
    }

    public void loadAll() throws IOException {
        List<ClientProfile> profiles = application.stateService.getProfiles();
        Map<ClientProfile, OptionalView> optionalViewMap = application.stateService.getOptionalViewMap();
        if (profiles == null)
            return;
        Path optionsFile = DirBridge.dir.resolve("options.json");
        if (!Files.exists(optionsFile))
            return;

        Type collectionType = new TypeToken<List<OptionalListEntry>>() {
        }.getType();

        try (Reader reader = IOHelper.newReader(optionsFile)) {
            List<OptionalListEntry> list = Launcher.gsonManager.gson.fromJson(reader, collectionType);
            for (OptionalListEntry entry : list) {
                ClientProfile selectedProfile = null;
                for (ClientProfile clientProfile : profiles) {
                    if (entry.profileUUID != null ? entry.profileUUID.equals(clientProfile.getUUID()) : clientProfile.getTitle().equals(entry.name))
                        selectedProfile = clientProfile;
                }
                if (selectedProfile == null) {
                    LogHelper.warning("Optional: profile %s(%s) not found", entry.name, entry.profileUUID);
                    continue;
                }
                OptionalView view = optionalViewMap.get(selectedProfile);
                for (OptionalListEntryPair entryPair : entry.enabled) {
                    try {
                        OptionalFile file = selectedProfile.getOptionalFile(entryPair.name);
                        if (file.visible) {
                            if (entryPair.mark)
                                view.enable(file, entryPair.installInfo != null && entryPair.installInfo.isManual, null);
                            else
                                view.disable(file, null);
                        }
                    } catch (Exception exc) {
                        LogHelper.warning("Optional: in profile %s markOptional mod %s failed", selectedProfile.getTitle(), entryPair.name);
                    }
                }
            }
        }
    }

    public static class OptionalListEntryPair {
        public String name;
        public boolean mark;
        public OptionalView.OptionalFileInstallInfo installInfo;

        public OptionalListEntryPair(OptionalFile optionalFile, boolean enabled, OptionalView.OptionalFileInstallInfo installInfo) {
            name = optionalFile.name;
            mark = enabled;
            this.installInfo = installInfo;
        }
    }

    public static class OptionalListEntry {
        public List<OptionalListEntryPair> enabled = new LinkedList<>();
        public String name;
        public UUID profileUUID;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OptionalListEntry that = (OptionalListEntry) o;
            return Objects.equals(profileUUID, that.profileUUID) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, profileUUID);
        }
    }
}
