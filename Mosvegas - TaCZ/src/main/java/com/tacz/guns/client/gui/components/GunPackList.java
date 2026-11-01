package com.tacz.guns.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.*;

public class GunPackList extends ContainerObjectSelectionList<GunPackList.Entry> {
    private final GunSmithTableScreen parent;
    private final List<Checkbox> gunPackList = new ArrayList<>();
    private final Set<String> selectedNamespaces = new HashSet<>();
    private final Checkbox byHandCheckbox;
    private final EditBox byName;

    public GunPackList(Minecraft pMinecraft, int pWidth, int pHeight, int pY0, int pY1,
                       Map<ResourceLocation, List<ResourceLocation>> recipes, GunSmithTableScreen parent) {
        super(pMinecraft, pWidth, pHeight, pY0, pY1);
        this.setRenderBackground(false);
        this.parent = parent;
        Set<String> namespaces = new HashSet<>();
        for (List<ResourceLocation> entry : recipes.values()) {
            entry.forEach((resourceLocation) -> namespaces.add(resourceLocation.getNamespace()));
        }

        this.byName = new EditBox(pMinecraft.font, 3, 0, 94, 10, Component.empty());
        this.byName.setHint(Component.translatable("gui.tacz.gun_smith_table.filter.search"));
        this.byName.setResponder((pText) -> {
            parent.init();
            parent.setIndexPage(0);
        });
        this.addEntry(new Entry(byName));

        this.byHandCheckbox = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.handgun"), false) {
            @Override
            public void onPress() {
                super.onPress();
                parent.init();
                parent.setIndexPage(0);
            }
        };
        this.addEntry(new Entry(byHandCheckbox));

        Checkbox checkbox1 = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.all"), true) {
            @Override
            public void onPress() {
                super.onPress();
                gunPackList.forEach((checkbox) -> checkbox.selected = this.selected);
                updateSelectedNamespaces();
            }
        };
        this.addEntry(new Entry(checkbox1));

        for (String namespace : namespaces) {
            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            Component name = packInfo == null ? Component.literal(namespace) : Component.translatable(packInfo.getName());

            Checkbox checkbox = new Checkbox(0, 0, 10, 10, name, namespace, true) {
                @Override
                public void onPress() {
                    super.onPress();
                    checkbox1.selected = gunPackList.stream().allMatch(Checkbox::selected);
                    updateSelectedNamespaces();
                }
            };
            gunPackList.add(checkbox);
            selectedNamespaces.add(namespace);
            this.addEntry(new Entry(checkbox));
        }
    }

    public String getSearchText() {
        return byName.getValue();
    }

    public boolean isByHandSelected() {
        return byHandCheckbox.selected;
    }

    public void setByHandSelected(boolean selected) {
        byHandCheckbox.selected = selected;
    }

    public Set<String> namespaceList() {
        return selectedNamespaces;
    }

    public void updateSelectedNamespaces() {
        selectedNamespaces.clear();
        gunPackList.forEach((checkbox) -> {
            if (checkbox.selected) {
                selectedNamespaces.add(checkbox.getId());
            }
        });
        parent.init();
        parent.setIndexPage(0);
    }

    public void resize(int width, int top, int bottom) {
        this.setSize(width, bottom - top);
        this.setY(top);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRight() - 2;
    }

    @Override
    public int getRowLeft() {
        return this.getX() + 4;
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - 4;
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        pGuiGraphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x80000000);
        this.enableScissor(pGuiGraphics);
        this.renderList(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        pGuiGraphics.disableScissor();

        int i = this.getScrollbarPosition();
        int j = i + 6;

        int i2 = this.getMaxScroll();
        if (i2 > 0) {
            int height = this.getBottom() - this.getY();
            int j2 = (int) ((float) (height * height) / (float) this.getMaxPosition());
            j2 = Mth.clamp(j2, 32, height - 8);
            int k1 = (int) this.getScrollAmount() * (height - j2) / i2 + this.getY();
            if (k1 < this.getY()) {
                k1 = this.getY();
            }
            pGuiGraphics.fill(i, k1, j, k1 + j2, -8355712);
            pGuiGraphics.fill(i, k1, j - 1, k1 + j2 - 1, -4144960);
        }
        this.renderDecorations(pGuiGraphics, pMouseX, pMouseY);
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final AbstractWidget widget;

        public Entry(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(widget);
        }

        @Override
        public void render(GuiGraphics pGuiGraphics, int pIndex, int pTop, int pLeft, int pWidth, int pHeight, int pMouseX, int pMouseY, boolean pHovering, float pPartialTick) {
            this.widget.setX(pLeft);
            this.widget.setY(pTop);
            this.widget.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(widget);
        }
    }

    public static class Checkbox extends AbstractButton {
        private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE = new ResourceLocation("widget/checkbox_selected_highlighted");
        private static final ResourceLocation CHECKBOX_SELECTED_SPRITE = new ResourceLocation("widget/checkbox_selected");
        private static final ResourceLocation CHECKBOX_HIGHLIGHTED_SPRITE = new ResourceLocation("widget/checkbox_highlighted");
        private static final ResourceLocation CHECKBOX_SPRITE = new ResourceLocation("widget/checkbox");
        protected boolean selected;
        protected final boolean showLabel;
        private String id;

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, String id, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
            this.id = id;
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected, boolean pShowLabel) {
            super(pX, pY, pWidth, pHeight, pMessage);
            this.selected = pSelected;
            this.showLabel = pShowLabel;
        }

        public String getId() {
            return id;
        }

        public void onPress() {
            this.selected = !this.selected;
        }

        public boolean selected() {
            return this.selected;
        }

        public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
            pNarrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
            if (this.active) {
                if (this.isFocused()) {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused"));
                } else {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered"));
                }
            }

        }

        public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            ResourceLocation sprite;
            if (this.selected) {
                sprite = this.isFocused() ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE;
            } else {
                sprite = this.isFocused() ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE;
            }
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            pGuiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
            pGuiGraphics.blitSprite(sprite, this.getX(), this.getY(), this.width, this.height);
            pGuiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (this.showLabel) {
                pGuiGraphics.drawString(font, this.getMessage(), this.getX() + this.width + 4, this.getY() + (this.height - 8) / 2, 14737632 | Mth.ceil(this.alpha * 255.0F) << 24);
            }

        }
    }
}
