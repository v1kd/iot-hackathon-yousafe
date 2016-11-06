#include <pebble.h>

#define KEY_BUTTON    0
#define KEY_VIBRATE   1
#define KEY_LONG      2

#define BUTTON_UP     0
#define BUTTON_SELECT 1
#define BUTTON_DOWN   2

static Window *s_main_window;
static TextLayer *s_text_layer;

static uint32_t accel_buffer[]={0,0,0,0,0,0,0,0};
static uint8_t position=0;
static uint8_t WIN=2;
static uint32_t TRESHOLD= 10000000;

/*
Implementation for fall detection was used from
https://github.com/Hitheshaum/fall-detection
*/


/******************************* AppMessage ***********************************/

static void send(int key, int message) {
  DictionaryIterator *iter;
  app_message_outbox_begin(&iter);

  dict_write_int(iter, key, &message, sizeof(int), true);

  app_message_outbox_send();
}

static void inbox_received_handler(DictionaryIterator *iterator, void *context) {
  // Get the first pair
  Tuple *t = dict_read_first(iterator);

  // Process all pairs present
  while(t != NULL) {
    // Process this pair's key
    switch(t->key) {
      case KEY_VIBRATE:
        // Trigger vibration
        text_layer_set_text(s_text_layer, "Vibrate!");
        vibes_short_pulse();
        break;
      default:
        APP_LOG(APP_LOG_LEVEL_INFO, "Unknown key: %d", (int)t->key);
        break;
    }

    // Get next pair, if any
    t = dict_read_next(iterator);
  }
}

static void inbox_dropped_handler(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
}

static void outbox_failed_handler(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
}

static void outbox_sent_handler(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
}

/********************************* Buttons ************************************/

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(s_text_layer, "Select");

  send(KEY_BUTTON, BUTTON_SELECT);
}

static void select_long_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(s_text_layer, "Select");

  send(KEY_LONG, BUTTON_SELECT);
}

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(s_text_layer, "Up");

  send(KEY_BUTTON, BUTTON_UP);
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(s_text_layer, "Down");

  send(KEY_BUTTON, BUTTON_DOWN);
}

static void click_config_provider(void *context) {
  // Assign button handlers
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
  
  // long click subscribe
  window_long_click_subscribe(BUTTON_ID_SELECT, 0, NULL, select_long_click_handler);
}

/******************************* main_window **********************************/

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_frame(window_layer);

  // Create main TextLayer
  s_text_layer = text_layer_create(bounds);
  text_layer_set_font(s_text_layer, fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD));
  text_layer_set_text(s_text_layer, "Open Android app and press any button.");
  text_layer_set_text_alignment(s_text_layer, GTextAlignmentCenter);
  layer_add_child(window_get_root_layer(window), text_layer_get_layer(s_text_layer));
#ifdef PBL_ROUND
  text_layer_enable_screen_text_flow_and_paging(s_text_layer, 5);
#endif
}

static void main_window_unload(Window *window) {
  // Destroy main TextLayer
  text_layer_destroy(s_text_layer);
}

static void accel_data_handler(AccelData *data, uint32_t num_samples){
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "acceleration read");
  accel_buffer[position]=(((data->x)*(data->x))+((data->y)*(data->y))+((data->z)*(data->z)));
  uint32_t minimum;
  uint32_t maximum;
  minimum=accel_buffer[0];
  maximum=accel_buffer[0];
  for ( uint8_t c = 1 ; c < WIN ; c++ ) {
    if ( accel_buffer[c] < minimum ) {
      minimum = accel_buffer[c];
    }
    if(accel_buffer[c]> maximum){
      maximum=accel_buffer[c];
    }
  }
  if((maximum-minimum)>TRESHOLD){
    //text_layer_set_text(text_layer, "Fall");
    APP_LOG(APP_LOG_LEVEL_DEBUG, "fall");
    /*DictionaryIterator *iter;
    app_message_outbox_begin(&iter);
    Tuplet value = TupletInteger(1, 42);
    dict_write_tuplet(iter, &value);
    app_message_outbox_send();*/
    
    // send message to phone
    send(KEY_BUTTON, BUTTON_UP);
  }
  if((position++)==(WIN-1)){
    position=0;
  }
  
}

static void init(void) {
  // Register callbacks
  app_message_register_inbox_received(inbox_received_handler);
  app_message_register_inbox_dropped(inbox_dropped_handler);
  app_message_register_outbox_failed(outbox_failed_handler);
  app_message_register_outbox_sent(outbox_sent_handler);
  accel_data_service_subscribe(1, accel_data_handler);
  // Open AppMessage
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  // Create main Window
  s_main_window = window_create();
  window_set_click_config_provider(s_main_window, click_config_provider);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload,
  });
  window_stack_push(s_main_window, true);
}

static void deinit(void) {
  // Destroy main Window
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}