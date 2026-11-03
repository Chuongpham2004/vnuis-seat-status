COMPOSE = docker compose -f infra/docker-compose.yml

.PHONY: up down migrate reset ps logs psql redis mqtt-sub app

up:
	$(COMPOSE) up -d --wait

down:
	$(COMPOSE) down

migrate:
	$(COMPOSE) --profile migrate run --rm flyway migrate

# Drop all data volumes, start fresh and re-seed.
reset:
	$(COMPOSE) down -v
	$(COMPOSE) up -d --wait
	$(COMPOSE) --profile migrate run --rm flyway migrate

ps:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs -f --tail=100

psql:
	docker exec -it lib-postgres psql -U app -d library

redis:
	docker exec -it lib-redis redis-cli

mqtt-sub:
	docker run --rm -it --network smart-library_default eclipse-mosquitto:2 \
		mosquitto_sub -h emqx -u monitor -P monitor_dev_pw -t 'library/#' -v

app:
	$(COMPOSE) --profile app up -d --build
