package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.Random;

public class EcosystemGame extends ApplicationAdapter {
	enum Terrain {
		Air(null),
		Soil(new Texture("soil.png"));

		public final Texture texture;

		Terrain(Texture texture) {
			this.texture = texture;
		}
	}

	class Tile {
		Terrain terrain = Terrain.Air;
		float humidity = 0;
	}

	SpriteBatch batch;

	int worldWidth, worldHeight;
	Tile[][] tiles;

	final Color colNoHumidity = new Color(1,1,1,1),
				colMaxHumidity = new Color(0,0,1,1);
	final Color dumpColor = new Color();

	public static int randomInt(Random random, int minInclusive, int maxExclusive) {
		return minInclusive + random.nextInt(maxExclusive - minInclusive);
	}
	
	@Override
	public void create () {
		batch = new SpriteBatch();
		worldWidth = 40;
		worldHeight = 30;
		tiles = new Tile[worldWidth][worldHeight];

		// Really crummy terrain generation
		Random random = new Random();

		int depth = randomInt(random, 1, 10);

		for (int x = 0; x < worldWidth; x++) {

			depth = randomInt(random, Math.max(1, depth - 2), Math.min(10, depth + 3));

			for (int y = 0; y < worldHeight; y++) {
				Tile tile = new Tile();
				if (y < depth) {
					tile.terrain = Terrain.Soil;
					tile.humidity = random.nextFloat();
				} else {
					tile.terrain = Terrain.Air;
				}
				tiles[x][y] = tile;
			}
		}
	}

	@Override
	public void render () {

		// update humidity
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = tiles[x][y];
				if (tile.terrain != Terrain.Air) { // TODO: Air humidity???
					// Evaporation
					if (y < (worldHeight-1)) {
						Tile above = tiles[x][y + 1];
						if (above.terrain == Terrain.Air) {
							tile.humidity *= 0.999f;
						}
					}

					// Osmosis

				}
			}
		}


		Gdx.gl.glClearColor((113f/255f), (149f/255f), (255f/255f), 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		batch.begin();

		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = tiles[x][y];
				Texture texture = tile.terrain.texture;
				if (texture != null) {

					// LibGDX is STUPID why does lerping a color edit the color??!?!??!?!?!?
					dumpColor.set(colNoHumidity);
					batch.setColor(dumpColor.lerp(colMaxHumidity, tile.humidity));
					batch.draw(texture, x * 16, y * 16);
				}
			}
		}

		batch.end();
	}
}
