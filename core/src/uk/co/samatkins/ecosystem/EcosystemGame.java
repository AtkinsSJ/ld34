package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.*;
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
		Terrain terrain;
	}

	SpriteBatch batch;

	int worldWidth, worldHeight;
	Tile[][] tiles;

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
				} else {
					tile.terrain = Terrain.Air;
				}
				tiles[x][y] = tile;
			}
		}
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor((113f/255f), (149f/255f), (255f/255f), 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		batch.begin();

		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Texture texture = tiles[x][y].terrain.texture;
				if (texture != null) {
					batch.draw(texture, x * 16, y * 16);
				}
			}
		}

		batch.end();
	}
}
