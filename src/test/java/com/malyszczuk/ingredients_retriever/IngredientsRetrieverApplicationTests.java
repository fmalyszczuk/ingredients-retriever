package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.Test;

@RequiresDocker
class IngredientsRetrieverApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
