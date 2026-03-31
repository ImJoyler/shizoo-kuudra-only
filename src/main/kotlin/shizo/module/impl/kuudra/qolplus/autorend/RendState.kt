package shizo.module.impl.kuudra.qolplus.autorend

 enum class RendState {
     IDLE,
     SWAPPED_BONE,
     SWAPPED_ATOM,
     WAITING_GUI,
     GUI_OPENED,
     SWAP_TO_ENDSTONE,
     ENDSTONE,
     REND,
     PULL
 }