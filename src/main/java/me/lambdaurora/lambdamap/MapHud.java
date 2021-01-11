/*
 * Copyright (c) 2020 LambdAurora <aurora42lambda@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lambdaurora.lambdamap;

import com.mojang.blaze3d.systems.RenderSystem;
import me.lambdaurora.lambdamap.map.MapChunk;
import me.lambdaurora.lambdamap.map.WorldMap;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.map.MapIcon;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;

public class MapHud implements AutoCloseable {
    private final MinecraftClient client;
    private final NativeImageBackedTexture texture = new NativeImageBackedTexture(128, 128, true);
    private final RenderLayer mapRenderLayer;
    private boolean visible = true;
    private boolean dirty = true;

    public MapHud(MinecraftClient client) {
        this.client = client;
        Identifier id = LambdaMap.id("hud");
        client.getTextureManager().registerTexture(id, this.texture);
        this.mapRenderLayer = RenderLayer.getText(id);
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void updateTexture(WorldMap map) {
        if (!this.visible || this.client.currentScreen != null)
            return;
        if (!this.dirty) return;
        else this.dirty = false;

        BlockPos corner = this.client.player.getBlockPos().add(-64, 0, -64);
        for (int z = 0; z < 128; ++z) {
            int absoluteZ = corner.getZ() + z;
            int chunkZ = absoluteZ >> 7;
            int chunkInnerZ = absoluteZ & 127;
            for (int x = 0; x < 128; ++x) {
                int absoluteX = corner.getX() + x;
                int chunkX = absoluteX >> 7;
                int chunkInnerX = absoluteX & 127;

                MapChunk chunk = map.getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    this.texture.getImage().setPixelColor(x, z, 0x00000000);
                    continue;
                }

                int pos = chunkInnerX + chunkInnerZ * 128;
                int color = chunk.colors[pos] & 255;
                if (color / 4 == 0) {
                    this.texture.getImage().setPixelColor(x, z, 0);
                } else {
                    this.texture.getImage().setPixelColor(x, z, MapColor.COLORS[color / 4].getRenderColor(color & 3));
                }
            }
        }

        this.texture.upload();
    }

    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!this.visible || this.client.currentScreen != null)
            return;

        float scaleFactor = (float) this.client.getWindow().getScaleFactor();
        float newScaleFactor;
        float scaleCompensation = 1.f;
        if (!(scaleFactor <= 2)) {
            newScaleFactor = (float) (this.client.getWindow().getScaleFactor() - 1);
            scaleCompensation = newScaleFactor / scaleFactor;
        }
        int width = (int) (this.client.getWindow().getFramebufferWidth() / scaleFactor);
        matrices.push();
        matrices.translate(width - 128 * scaleCompensation, 0, 1);
        matrices.scale(scaleCompensation, scaleCompensation, 0);
        RenderSystem.enableTexture();
        Matrix4f model = matrices.peek().getModel();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(this.mapRenderLayer);
        this.vertex(vertexConsumer, model, 0.f, 128.f, 0.f, 1.f, light);
        this.vertex(vertexConsumer, model, 128.f, 128.f, 1.f, 1.f, light);
        this.vertex(vertexConsumer, model, 128.f, 0.f, 1.f, 0.f, light);
        this.vertex(vertexConsumer, model, 0.f, 0.f, 0.f, 0.f, light);

        this.renderPlayerIcon(matrices, vertexConsumers, light);

        matrices.push();
        BlockPos pos = this.client.player.getBlockPos();
        String str = String.format("X: %d Y: %d Z: %d", pos.getX(), pos.getY(), pos.getZ());
        int strWidth = this.client.textRenderer.getWidth(str);
        this.client.textRenderer.draw(str, 64 - strWidth / 2.f, 130, 0xffffffff, true, model, vertexConsumers, false, 0, light);
        matrices.pop();
        matrices.pop();
    }

    private void vertex(VertexConsumer vertexConsumer, Matrix4f model, float x, float y,
                        float u, float v, int light) {
        vertexConsumer.vertex(model, x, y, 0.f).color(255, 255, 255, 255)
                .texture(u, v).light(light).next();
    }

    private void renderPlayerIcon(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.translate(64.f, 64.f, .1f);
        matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(this.client.player.yaw));
        matrices.scale(4.f, 4.f, 3.f);
        matrices.translate(-0.125, 0.125, 0.0);
        byte playerIconId = MapIcon.Type.PLAYER.getId();
        float g = (float) (playerIconId % 16) / 16.f;
        float h = (float) (playerIconId / 16) / 16.f;
        float m = (float) (playerIconId % 16 + 1) / 16.f;
        float n = (float) (playerIconId / 16 + 1) / 16.f;
        Matrix4f model = matrices.peek().getModel();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(LambdaMap.MAP_ICONS);
        this.vertex(vertexConsumer, model, -1.f, 1.f, g, h, light);
        this.vertex(vertexConsumer, model, 1.f, 1.f, m, h, light);
        this.vertex(vertexConsumer, model, 1.f, -1.f, m, n, light);
        this.vertex(vertexConsumer, model, -1.f, -1.f, g, n, light);
        matrices.pop();
    }

    public void close() {
        this.texture.close();
    }
}
