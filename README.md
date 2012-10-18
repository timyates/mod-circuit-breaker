Circuit breaker model for Vert.x

Under construction...  Needs severe amounts of testing

Example config:

    { 
      "address" : "breaker-addr",
      "max_failures" : 5,
      "call_timeout" : 10,
      "reset_time"   : 60,
      
      "circuits": {
        "vertx.mongo-persistor-v1.1" : {
          "circuitConfig": {
            "instances": 1
          },
          "modConfig": {
            "address" : "test.my_persistor",
            "host"    : "192.168.1.100",
            "port"    : 27000,
            "db_name" : "my_db"
          }
        },
        "vertx.mailer-v1.0" : {
          "circuitConfig": {
            "instances": 1
          },
          "modConfig": {
            "address": "test.my_mailer"
            "host": "smtp.googlemail.com",
            "port": 465,
            "ssl": true,
            "auth": true,
            "username": "tim",
            "password": "password"
          }
        }
      }
    }
