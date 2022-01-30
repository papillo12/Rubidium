package me.jellysquid.mods.sodium.mixin.features.item;

import me.jellysquid.mods.sodium.render.terrain.quad.ModelQuadView;
import me.jellysquid.mods.sodium.interop.vanilla.vertex.VanillaVertexFormats;
import me.jellysquid.mods.sodium.render.vertex.VertexDrain;
import me.jellysquid.mods.sodium.interop.vanilla.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.render.terrain.quad.ModelQuadUtil;
import me.jellysquid.mods.sodium.util.packed.ColorARGB;
import me.jellysquid.mods.sodium.util.rand.XoRoShiRoRandom;
import me.jellysquid.mods.sodium.interop.vanilla.mixin.ItemColorProviderRegistry;
import me.jellysquid.mods.sodium.util.DirectionUtil;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    private final XoRoShiRoRandom random = new XoRoShiRoRandom();

    @Shadow
    @Final
    private ItemColors colors;

    /**
     * @reason Avoid allocations
     * @author JellySquid
     */
    @Overwrite
    public void renderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay, MatrixStack matrices, VertexConsumer vertices) {
        XoRoShiRoRandom random = this.random;

        for (Direction direction : DirectionUtil.ALL_DIRECTIONS) {
            List<BakedQuad> quads = model.getQuads(null, direction, random.setSeedAndReturn(42L));

            if (!quads.isEmpty()) {
                this.renderBakedItemQuads(matrices, vertices, quads, stack, light, overlay);
            }
        }

        List<BakedQuad> quads = model.getQuads(null, null, random.setSeedAndReturn(42L));

        if (!quads.isEmpty()) {
            this.renderBakedItemQuads(matrices, vertices, quads, stack, light, overlay);
        }
    }

    /**
     * @reason Use vertex building intrinsics
     * @author JellySquid
     */
    @Overwrite
    public void renderBakedItemQuads(MatrixStack matrices, VertexConsumer vertexConsumer, List<BakedQuad> quads, ItemStack stack, int light, int overlay) {
        MatrixStack.Entry entry = matrices.peek();

        ItemColorProvider colorProvider = null;

        QuadVertexSink drain = VertexDrain.of(vertexConsumer)
                .createSink(VanillaVertexFormats.QUADS);
        drain.ensureCapacity(quads.size() * 4);

        for (BakedQuad bakedQuad : quads) {
            int color = 0xFFFFFFFF;

            if (!stack.isEmpty() && bakedQuad.hasColor()) {
                if (colorProvider == null) {
                    colorProvider = ((ItemColorProviderRegistry) this.colors).getColorProvider(stack);
                }

                if (colorProvider == null) {
                    color = this.colors.getColor(stack, bakedQuad.getColorIndex());
                } else {
                    color = ColorARGB.toABGR((colorProvider.getColor(stack, bakedQuad.getColorIndex())), 255);
                }
            }

            ModelQuadView quad = ((ModelQuadView) bakedQuad);

            for (int i = 0; i < 4; i++) {
                drain.writeQuad(entry, quad.getX(i), quad.getY(i), quad.getZ(i), color, quad.getTexU(i), quad.getTexV(i),
                        light, overlay, ModelQuadUtil.getFacingNormal(bakedQuad.getFace()));
            }

            SpriteUtil.markSpriteActive(quad.getSprite());
        }

        drain.flush();
    }
}
