package BallCore.Groups

object Extensions:
    extension [A, B](e: Either[A, B])
        def guard(onFalse: A)(cond: B => Boolean): Either[A, B] =
            e.flatMap { data =>
                if cond(data) then
                    Right(data)
                else
                    Left(onFalse)
            }
            
