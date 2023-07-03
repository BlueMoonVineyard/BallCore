package BallCore.PolygonEditor

trait Model[Self, Msg, Action]:
	def update(msg: Msg): (Self, List[Action])
